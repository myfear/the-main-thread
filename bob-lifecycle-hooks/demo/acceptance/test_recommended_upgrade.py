import unittest
from datetime import date

from app.release_policy import Release, recommended_upgrade


class RecommendedUpgradeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.releases = [
            Release("1.0", date(2025, 12, 31)),
            Release("2.0", date(2027, 6, 30)),
            Release("2.1", date(2028, 6, 30)),
        ]
        self.today = date(2026, 8, 13)

    def test_returns_latest_supported_release_for_unsupported_current_version(self) -> None:
        self.assertEqual(
            "2.1",
            recommended_upgrade(self.releases, "1.0", self.today),
        )

    def test_returns_none_when_current_version_is_still_supported(self) -> None:
        self.assertIsNone(
            recommended_upgrade(self.releases, "2.0", self.today),
        )

    def test_rejects_an_unknown_current_version(self) -> None:
        with self.assertRaisesRegex(ValueError, "Unknown release: 9.9"):
            recommended_upgrade(self.releases, "9.9", self.today)


if __name__ == "__main__":
    unittest.main()

