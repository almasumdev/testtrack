/**
 * TestTrack — Google Group + Play track verification service
 *
 * Paste this into script.google.com and redeploy after every edit. This file is the copy under
 * version control; the one running at /exec is whatever was last deployed, which is not the same
 * thing and has caught us out before.
 *
 *   POST {idToken}                   -> { email, isMember }        the membership gate
 *   GET  ?action=listing&pkg=X       -> { publiclyListed, code }   closed-testing check
 *
 * Deploy as: Web app · Execute as "Me" · Who has access "Anyone".
 * After ANY edit: Deploy > Manage deployments > pencil > Version: New version > Deploy.
 * Editing and saving alone does NOT change what /exec runs.
 */

const GROUP     = 'googleclosetesting@googlegroups.com';
const CLIENT_ID = '719099120665-ih3jnp1ebahvbpbmapa3guduhcsk2dhm.apps.googleusercontent.com';

/**
 * The roster is read fresh on every check. There is no cache.
 *
 * It used to be held for 120 seconds, which made a new joiner press Verify three or four times
 * before the list caught up with them, and that is the moment somebody decides the app is broken
 * and stops. Correct now beats cheap.
 *
 * The bill: GroupsApp reads are capped at 2,000 a day and every check now spends one, so 2,000 is
 * the ceiling on checks per day across all testers put together. A tester spends a handful, so a
 * dozen of them are nowhere near it. Somewhere in the low hundreds of testers this needs
 * revisiting, and [spentToday] is how to see it coming rather than find out from a bad morning.
 */
function readMembers() {
  spend();
  return GroupsApp.getGroupByEmail(GROUP).getUsers().map(u => norm(u.getEmail()));
}

/**
 * The roster, or null when we could not get one.
 *
 * Null rather than an empty list on purpose. An empty roster is a failed read far more often than
 * it is an empty group, and the caller must not be able to mistake one for the other: answering
 * "not a member" because the list could not be read tells twelve people they are out of a group
 * they are in, and it sounds like a fact rather than a failure, so nobody questions it.
 */
function membersOrNull() {
  try {
    const members = readMembers();
    return members.length ? members : null;
  } catch (err) {
    // Almost always the daily read quota. Visible under Executions, which is where to look when
    // every tester reports the same failure at the same time of day.
    Logger.log('roster read failed: ' + err);
    return null;
  }
}

/** Is the signed-in account a member? Verdict is computed here, never on the device. */
function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    if (!body.idToken) return json({ error: 'No sign-in token was sent. Sign in again and retry.' });

    const res = UrlFetchApp.fetch(
      'https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(body.idToken),
      { muteHttpExceptions: true });
    if (res.getResponseCode() !== 200) {
      return json({ error: 'Google would not confirm your sign-in. Sign out and sign in again.' });
    }

    const info = JSON.parse(res.getContentText());
    if (info.aud !== CLIENT_ID) {
      return json({ error: 'This sign-in was issued for a different app. Reinstall TestTrack from the release page.' });
    }
    if (String(info.email_verified) !== 'true') {
      return json({ error: 'This Google account has no verified email address, so it cannot be checked against the group.' });
    }

    const members = membersOrNull();
    if (!members) {
      return json({ error: 'The group list could not be read just now. Try again in a moment.' });
    }

    const me = norm(info.email);
    return json({ email: me, isMember: members.indexOf(me) !== -1 });
  } catch (err) {
    Logger.log('doPost failed: ' + err);
    return json({ error: 'The membership check could not run. Try again in a moment.' });
  }
}

function doGet(e) {
  try {
    const p = (e && e.parameter) || {};

    /**
     * Does this package have a public Play listing?
     *
     * 404 means it is either on a testing track or does not exist — ambiguous alone. The app
     * pairs it with a device-side check that the package is installed AND that Google Play was
     * the installer, which rules out "does not exist". Together: on Play but not public =
     * testing track.
     *
     * Runs server-side so a patched APK cannot fake the answer. Nothing calls this yet; the
     * device-side half is in InstalledApps.kt and was never wired up to it.
     */
    if (p.action === 'listing' && p.pkg) {
      const url = 'https://play.google.com/store/apps/details?id=' + encodeURIComponent(p.pkg);
      const res = UrlFetchApp.fetch(url, { muteHttpExceptions: true, followRedirects: true });
      const code = res.getResponseCode();
      return json({ pkg: p.pkg, code: code, publiclyListed: code === 200 });
    }

    // The roster dump that used to live here is gone. It returned every member's address behind
    // a key written in this file, and a file like this gets pasted into a chat window sooner or
    // later, which is all it takes. Use testMembers() from the editor instead: same answer, no
    // way in from outside.
    return json({ error: 'unauthorized' });
  } catch (err) {
    return json({ error: String(err) });
  }
}

/** Run this one from the editor to sanity-check. doPost/doGet cannot be run there. */
function testMembers() {
  const members = membersOrNull();
  Logger.log('COUNT: ' + (members ? members.length : 'READ FAILED'));
  Logger.log('READS TODAY: ' + spentToday() + ' of 2000');
  if (members) Logger.log(members.join('\n'));
}

/**
 * Groups reads used today, in one self-resetting property.
 *
 * More important now than it was, because there is nothing between us and the limit any more. At
 * 2,000 reads getUsers() throws and every check fails until the quota comes back. A number you
 * can look at turns that from a surprise into a warning.
 *
 * A gauge, not a mirror. Google's quota resets 24 hours after the first request rather than at
 * any particular hour, so a rolling window cannot be reproduced by a calendar day and this counts
 * a UTC one instead. Near the limit the two disagree by up to a day's worth of reads, which is
 * fine for "are we getting close" and no good at all for "how many exactly do we have left".
 */
function today() {
  return Utilities.formatDate(new Date(), 'Etc/UTC', 'yyyy-MM-dd');
}

function spentToday() {
  const raw = PropertiesService.getScriptProperties().getProperty('reads');
  if (!raw) return 0;
  const rec = JSON.parse(raw);
  return rec.day === today() ? rec.count : 0;
}

function spend() {
  PropertiesService.getScriptProperties()
    .setProperty('reads', JSON.stringify({ day: today(), count: spentToday() + 1 }));
}

/**
 * Gmail treats dots and +suffixes as insignificant, so the group and the ID token can hold
 * different strings for the same account. 4 of the current 12 members need this.
 */
function norm(email) {
  const parts = String(email).toLowerCase().split('@');
  let user = parts[0];
  let domain = parts[1];
  if (domain === 'gmail.com' || domain === 'googlemail.com') {
    user = user.split('+')[0].replace(/\./g, '');
    domain = 'gmail.com';
  }
  return user + '@' + domain;
}

function json(o) {
  return ContentService.createTextOutput(JSON.stringify(o))
    .setMimeType(ContentService.MimeType.JSON);
}
