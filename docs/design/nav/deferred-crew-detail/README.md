Crew detail work saved for later, following the user's Run-tab-only scope correction.

The app's detail page was restored to its prior version. The approved Lobby seat-map layout,
standard tile background/border, and matching Lobby loading state remain in the app.

`tracked-changes.patch` preserves the detail-only tracked file changes. The two new detail
components are saved as `.kt.txt` files. `CrewLoading.kt.txt` and `CrewLobbyRow.kt.txt` preserve
the shared-file versions from the detail work; merge their detail-specific changes rather than
overwriting newer Lobby work when resuming.

The separate `../crew-detail-current.html` gallery contains native renders from that deferred work.
