"""Run inside the dev backend container; read sample icon payloads from stdin."""

import asyncio
import json
import sys
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.engine import make_url

from app.core.config import settings
from app.db.models import Crew, Seat, User, UserApp
from app.db.session import dispose, factory
from app.services import icons


async def main():
    config = settings()
    assert config.environment == "dev", "Only the dev stack may be changed"
    assert make_url(config.database_url).database == "testtrack_dev", "Wrong database"
    assert "testtrack-dev." in config.public_api_url, "Wrong public icon host"
    payload = json.load(sys.stdin)
    async with factory()() as db:
        crew_id = await db.scalar(
            select(Crew.id).join(Seat, Seat.crew_id == Crew.id).join(User, User.id == Seat.owner_id)
            .where(Crew.number == 58, User.email == "nam.bd88@gmail.com")
        )
        assert crew_id is not None, "Expected dev crew does not belong to this account"
        changed = []
        for item in payload:
            assert item["package"].startswith("dev.seedapps.member")
            seat = await db.scalar(
                select(Seat)
                .where(Seat.crew_id == crew_id, Seat.device_fp.like("seed-my-apps-%"),
                       Seat.package_name == item["package"])
            )
            assert seat is not None, f"Missing demo seat: {item['package']}"
            owner = await db.get(User, seat.owner_id)
            assert owner.email != "nam.bd88@gmail.com", "Do not change the real account's apps"
            app = await db.get(UserApp, seat.app_id) if seat.app_id else await db.scalar(
                select(UserApp).where(UserApp.owner_id == seat.owner_id, UserApp.package_name == seat.package_name)
            )
            if app is None:
                app = UserApp(owner_id=seat.owner_id, package_name=seat.package_name,
                              name=seat.app_name, verified_by="play", verified_at=datetime.now(UTC))
                db.add(app)
                await db.flush()
            seat.app_id = app.id
            if app.icon:
                continue
            app.icon = await icons.store(db, item["image"])
            changed.append({"name": app.name, "package": app.package_name, "icon": app.icon})
        await db.commit()
        print(json.dumps({"crew": 58, "updated": changed}))
    await dispose()


asyncio.run(main())
