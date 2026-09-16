"""Secret-safe diagnostics for disposable DATA-03 integration failures."""

import unittest

from integration_data_stack import safe_mysql_log_line


class MysqlDiagnosticTest(unittest.TestCase):
    def test_redacts_generated_credential(self):
        credential = "a" * 64
        result = safe_mysql_log_line(f"mysqld ERROR key {credential} rejected")
        self.assertIn("[generated credential redacted]", result)
        self.assertNotIn(credential, result)

    def test_withholds_password_bearing_line(self):
        self.assertEqual(
            safe_mysql_log_line("entrypoint failed while reading password file"),
            "[sensitive MySQL diagnostic withheld]",
        )

    def test_ignores_unrelated_output(self):
        self.assertIsNone(safe_mysql_log_line("mysqld ready for connections"))


if __name__ == "__main__":
    unittest.main()
