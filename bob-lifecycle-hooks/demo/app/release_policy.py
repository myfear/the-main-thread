from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Release:
    version: str
    support_ends: date


def supported_releases(releases: list[Release], on_date: date) -> list[Release]:
    """Return supported releases in the same order as the input."""
    return [release for release in releases if release.support_ends >= on_date]

