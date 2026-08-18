import unittest
from datetime import date

from app.release_policy import Release, supported_releases


class SupportedReleasesTest(unittest.TestCase):
    def test_filters_unsupported_releases_without_reordering(self) -> None:
        releases = [
            Release("1.0", date(2025, 12, 31)),
            Release("2.0", date(2027, 6, 30)),
            Release("2.1", date(2028, 6, 30)),
        ]

        result = supported_releases(releases, date(2026, 8, 13))

        self.assertEqual(["2.0", "2.1"], [release.version for release in result])


if __name__ == "__main__":
    unittest.main()

