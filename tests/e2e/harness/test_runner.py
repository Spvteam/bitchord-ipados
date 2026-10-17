"""BitChord E2E Test Runner and Reporter."""

import argparse
import importlib
import importlib.util
import inspect
import json
import os
import sys
import time
import traceback
from typing import List, Dict, Any, Callable, Tuple


class TestResult:
    def __init__(self, name: str, feature: int, tier: int, category: str):
        self.name = name
        self.feature = feature
        self.tier = tier
        self.category = category
        self.status = "PENDING"
        self.duration_ms = 0.0
        self.error_message: str = ""
        self.traceback: str = ""


class E2ETestRunner:
    def __init__(self, root_dir: str):
        self.root_dir = root_dir
        self.results: List[TestResult] = []

    def discover_tests(self, tier: str = "all", feature_filter: int = 0) -> List[Tuple[str, str, Callable, int, int]]:
        """
        Discovers test functions from tier directories.
        Returns list of (module_name, func_name, callable, tier_num, feature_num)
        """
        discovered = []
        base_e2e_dir = os.path.join(self.root_dir, "tests", "e2e")

        tier_folders = {
            1: ("tier1_features", 1),
            2: ("tier2_boundary", 2),
            3: ("tier3_cross_feature", 3),
            4: ("tier4_scenarios", 4)
        }

        selected_tiers = [1, 2, 3, 4] if tier == "all" else [int(tier)]

        for t_num in selected_tiers:
            folder_name, t_val = tier_folders[t_num]
            folder_path = os.path.join(base_e2e_dir, folder_name)
            if not os.path.exists(folder_path):
                continue

            for file in sorted(os.listdir(folder_path)):
                if file.startswith("test_") and file.endswith(".py"):
                    file_path = os.path.join(folder_path, file)
                    mod_name = f"tests.e2e.{folder_name}.{file[:-3]}"
                    
                    # Extract feature number from filename if present
                    f_num = 0
                    if "_f" in file:
                        try:
                            f_part = file.split("_f")[1].split("_")[0]
                            f_num = int(f_part)
                        except (ValueError, IndexError):
                            f_num = 0

                    if feature_filter > 0 and f_num > 0 and f_num != feature_filter:
                        continue

                    # Import module dynamically
                    spec = importlib.util.spec_from_file_location(mod_name, file_path)
                    if spec and spec.loader:
                        mod = importlib.util.module_from_spec(spec)
                        sys.modules[mod_name] = mod
                        spec.loader.exec_module(mod)

                        # Find all test_* functions or TestCase classes
                        for attr_name in dir(mod):
                            if attr_name.startswith("test_"):
                                attr = getattr(mod, attr_name)
                                if callable(attr):
                                    discovered.append((mod_name, attr_name, attr, t_val, f_num))
                            elif attr_name.endswith("Test") or attr_name.startswith("Test"):
                                cls = getattr(mod, attr_name)
                                if isinstance(cls, type):
                                    for m_name in dir(cls):
                                        if m_name.startswith("test_"):
                                            m_attr = getattr(cls, m_name)
                                            if callable(m_attr):
                                                def make_bound_call(c_cls, c_mname):
                                                    return lambda: getattr(c_cls(), c_mname)()
                                                discovered.append((mod_name, f"{attr_name}.{m_name}", make_bound_call(cls, m_name), t_val, f_num))

        return discovered

    def run(self, tier: str = "all", feature_filter: int = 0, verbose: bool = False, fail_fast: bool = False) -> Dict[str, Any]:
        tests = self.discover_tests(tier=tier, feature_filter=feature_filter)
        total_count = len(tests)
        passed = 0
        failed = 0
        errors = 0

        print(f"\n=======================================================")
        print(f"  BitChord iPadOS Port - E2E Test Suite Execution")
        print(f"  Tiers: {tier.upper()} | Feature Filter: {feature_filter or 'ALL'}")
        print(f"  Discovered: {total_count} test cases")
        print(f"=======================================================\n")

        start_all = time.time()

        for idx, (mod_name, func_name, func, t_val, f_num) in enumerate(tests, 1):
            test_id = f"[{idx}/{total_count}] Tier {t_val} | F{f_num or 'X'}: {func_name}"
            result = TestResult(func_name, f_num, t_val, mod_name)
            t_start = time.time()

            try:
                func()
                result.status = "PASSED"
                passed += 1
                result.duration_ms = (time.time() - t_start) * 1000.0
                if verbose:
                    print(f"  [PASS] {test_id} ({result.duration_ms:.1f}ms)")
                else:
                    sys.stdout.write(".")
                    sys.stdout.flush()
            except AssertionError as ae:
                result.status = "FAILED"
                result.error_message = str(ae)
                result.traceback = traceback.format_exc()
                result.duration_ms = (time.time() - t_start) * 1000.0
                failed += 1
                print(f"\n  [FAIL] {test_id}: {ae}")
                if fail_fast:
                    self.results.append(result)
                    break
            except Exception as e:
                result.status = "ERROR"
                result.error_message = f"{type(e).__name__}: {str(e)}"
                result.traceback = traceback.format_exc()
                result.duration_ms = (time.time() - t_start) * 1000.0
                errors += 1
                print(f"\n  [ERROR] {test_id}: {e}")
                if fail_fast:
                    self.results.append(result)
                    break

            self.results.append(result)

        total_duration = time.time() - start_all
        print(f"\n\n-------------------------------------------------------")
        print(f"  Execution Summary:")
        print(f"  Total:  {len(self.results)}")
        print(f"  Passed: {passed}")
        print(f"  Failed: {failed}")
        print(f"  Errors: {errors}")
        print(f"  Time:   {total_duration:.2f}s")
        print(f"-------------------------------------------------------")

        is_success = (failed == 0 and errors == 0 and total_count > 0)
        status_text = "ALL TESTS PASSED - 100% SUCCESS" if is_success else "TEST SUITE FAILED"
        print(f"  Result: {status_text}")
        print(f"=======================================================\n")

        report = {
            "summary": {
                "total": len(self.results),
                "passed": passed,
                "failed": failed,
                "errors": errors,
                "duration_seconds": round(total_duration, 3),
                "success": is_success
            },
            "results": [
                {
                    "name": r.name,
                    "tier": r.tier,
                    "feature": r.feature,
                    "category": r.category,
                    "status": r.status,
                    "duration_ms": round(r.duration_ms, 2),
                    "error": r.error_message
                }
                for r in self.results
            ]
        }
        return report


def main():
    parser = argparse.ArgumentParser(description="BitChord iPadOS Port E2E Test Runner")
    parser.add_argument("--tier", default="all", choices=["1", "2", "3", "4", "all"], help="Test tier to run")
    parser.add_argument("--feature", type=int, default=0, help="Feature number (1-20) to filter")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose test progress")
    parser.add_argument("--failfast", "-f", action="store_true", help="Stop on first failure")
    parser.add_argument("--report", type=str, default="", help="Path to write JSON test report")

    args = parser.parse_args()

    # Ensure repository root is on sys.path
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    if root_dir not in sys.path:
        sys.path.insert(0, root_dir)

    runner = E2ETestRunner(root_dir)
    report = runner.run(tier=args.tier, feature_filter=args.feature, verbose=args.verbose, fail_fast=args.failfast)

    if args.report:
        report_dir = os.path.dirname(os.path.abspath(args.report))
        os.makedirs(report_dir, exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"Saved test report to {args.report}")

    sys.exit(0 if report["summary"]["success"] else 1)


if __name__ == "__main__":
    main()
