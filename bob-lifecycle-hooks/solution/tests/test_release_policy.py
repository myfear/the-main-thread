import unittest
from datetime import date

from app.release_policy import Release, recommended_upgrade, supported_releases


class SupportedReleasesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.releases = [
            Release("1.0", date(2025, 12, 31)),
            Release("2.0", date(2027, 6, 30)),
            Release("2.1", date(2028, 6, 30)),
        ]
        self.today = date(2026, 8, 13)

    def test_filters_unsupported_releases_without_reordering(self) -> None:
        result = supported_releases(self.releases, self.today)

        self.assertEqual(["2.0", "2.1"], [release.version for release in result])

    def test_recommends_the_longest_supported_release(self) -> None:
        self.assertEqual(
            "2.1",
            recommended_upgrade(self.releases, "1.0", self.today),
        )

    def test_returns_none_without_a_supported_upgrade(self) -> None:
        releases = [Release("1.0", date(2025, 12, 31))]

        self.assertIsNone(recommended_upgrade(releases, "1.0", self.today))


if __name__ == "__main__":
    unittest.main()

