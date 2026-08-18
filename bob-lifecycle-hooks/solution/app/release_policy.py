from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Release:
    version: str
    support_ends: date


def supported_releases(releases: list[Release], on_date: date) -> list[Release]:
    """Return supported releases in the same order as the input."""
    return [release for release in releases if release.support_ends >= on_date]


def recommended_upgrade(
    releases: list[Release], current_version: str, on_date: date
) -> str | None:
    """Return the longest-supported upgrade when the current release is unsupported."""
    current = next(
        (release for release in releases if release.version == current_version),
        None,
    )
    if current is None:
        raise ValueError(f"Unknown release: {current_version}")
    if current.support_ends >= on_date:
        return None

    candidates = supported_releases(releases, on_date)
    if not candidates:
        return None
    return max(candidates, key=lambda release: release.support_ends).version

