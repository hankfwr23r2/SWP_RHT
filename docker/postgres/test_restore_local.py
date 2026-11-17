"""Regression checks for restore completion and failure handling (no live database)."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import Mock, patch


class RestoreLocalTest(unittest.TestCase):
    def run_restore(self, failure=None):
        source = Path(__file__).with_name("restore_local.py")
        connection = Mock()

        def run(sql):
            if failure == "restore" and sql.startswith("INSERT"):
                raise RuntimeError("restore failed")
            if failure == "repair" and "$repair_sequences$" in sql:
                raise RuntimeError("repair failed")

        connection.run.side_effect = run
        native = types.ModuleType("pg8000.native")
        native.Connection = Mock(return_value=connection)
        driver = types.ModuleType("pg8000")
        driver.native = native
        output = io.StringIO()
        exit_code = 0

        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            (fixture / "backup.sql").write_text("CREATE TABLE example(id integer);\nINSERT INTO example VALUES (98);\n", encoding="utf-8")
            (fixture / "repair_sequences.sql").write_text(source.with_name("repair_sequences.sql").read_text(encoding="utf-8"), encoding="utf-8")
            spec = importlib.util.spec_from_file_location("restore_local_under_test", source)
            module = importlib.util.module_from_spec(spec)
            with patch.dict(sys.modules, {"pg8000": driver, "pg8000.native": native}):
                spec.loader.exec_module(module)
                module.__file__ = str(fixture / "restore_local.py")
                with patch.object(sys, "argv", [str(source), "--file", str(fixture / "backup.sql")]), contextlib.redirect_stdout(output):
                    try:
                        module.main()
                    except SystemExit as error:
                        exit_code = error.code

        return exit_code, connection, output.getvalue()

    def test_success_repairs_sequences_after_import_and_closes_connection(self):
        code, connection, output = self.run_restore()
        self.assertEqual(code, 0)
        statements = [call.args[0] for call in connection.run.call_args_list]
        self.assertEqual(len(statements), 3)
        self.assertTrue(statements[1].startswith("INSERT"))
        self.assertIn("DO $repair_sequences$", statements[2])
        connection.close.assert_called_once()
        self.assertIn("đồng bộ bộ đếm ID!", output)

    def test_restore_failure_stops_before_repair_and_reports_failure(self):
        code, connection, output = self.run_restore("restore")
        self.assertEqual(code, 1)
        self.assertEqual(connection.run.call_count, 2)
        connection.close.assert_called_once()
        self.assertNotIn("đồng bộ hoàn toàn", output)

    def test_repair_failure_reports_failure_and_closes_connection(self):
        code, connection, output = self.run_restore("repair")
        self.assertEqual(code, 1)
        connection.close.assert_called_once()
        self.assertIn("repair failed", output)
        self.assertNotIn("đồng bộ hoàn toàn", output)


if __name__ == "__main__":
    unittest.main()
