import unittest

from app.inspection import inspect_text_report
from app.text_check import check_text
from app.url_check import inspect_urls


class TextInspectionTests(unittest.TestCase):
    def test_pressure_message_is_high_risk(self):
        result = check_text("URGENT: your account is blocked. Send the OTP now!")
        self.assertEqual(result["risk"], "High")
        self.assertTrue(any("code" in reason.casefold() for reason in result["reason"]))

    def test_ordinary_message_does_not_claim_fact_checking(self):
        result = inspect_text_report("Our appointment is at 10 tomorrow.")
        self.assertIn("not proof", result["accessible_description"])
        self.assertEqual(result["input_type"], "text")
        self.assertIsInstance(result["next_actions"], list)

    def test_link_findings_are_local_and_explainable(self):
        findings = inspect_urls("Please open https://bit.ly/claim-now")
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0]["risk"], "Medium")
        self.assertTrue(any("Shortened" in signal for signal in findings[0]["signals"]))


if __name__ == "__main__":
    unittest.main()
