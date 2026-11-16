"""Verify a Refundavio App Tester receipt with Amazon's RVS Cloud Sandbox."""

import argparse
import json
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, build_opener


SKU = "app.refundavio.premium"
PACKAGE = "app.refundavio.qa"
SANDBOX = "https://appstore-sdk.amazon.com/sandbox/version/1.0/verifyReceiptId"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def verify(evidence):
    user, receipt = (evidence[field] for field in ("userId", "receiptId"))
    if evidence["sku"] != SKU or not all(isinstance(value, str) and value for value in (user, receipt)):
        raise ValueError("The App Tester evidence is missing the expected entitlement identifiers.")
    # Amazon's sandbox accepts a non-empty placeholder as the shared secret.
    url = f"{SANDBOX}/developer/refundavio-sandbox/user/{quote(user, safe='')}/receiptId/{quote(receipt, safe='')}"
    try:
        with build_opener(NoRedirect).open(url, timeout=20) as result:
            data = json.load(result)
    except HTTPError as error:
        raise ValueError(f"Amazon RVS Sandbox returned HTTP {error.code}.") from None
    except URLError:
        raise ValueError("The Amazon RVS Sandbox connection failed.") from None
    if (data.get("productId") != SKU or data.get("productType") != "ENTITLED"
            or data.get("testTransaction") is not True or data.get("receiptId") != receipt
            or "cancelDate" not in data or data["cancelDate"] is not None):
        raise ValueError("Amazon RVS did not confirm an active sandbox receipt for Refundavio Premium.")
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("phase", choices=("purchase", "restore"))
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def read_evidence(phase):
        return json.loads(subprocess.check_output(
            adb + ["exec-out", "run-as", PACKAGE, "cat", f"files/premium-{phase}.json"], timeout=15))

    try:
        evidence = read_evidence(args.phase)
        if args.phase == "restore" and evidence != read_evidence("purchase"):
            raise ValueError("The restored receipt or test account differs from the verified purchase.")
        verified = verify(evidence)
        if args.phase == "purchase":
            subprocess.run(adb + ["shell", "-T", f"run-as {PACKAGE} sh -c 'cat > files/premium-verified.json'"],
                           input=json.dumps(verified).encode(), check=True, timeout=15)
    except (KeyError, ValueError, subprocess.SubprocessError) as error:
        if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError):
            print(str(error), file=sys.stderr)
        else:
            print("Could not read or save the App Tester receipt evidence.", file=sys.stderr)
        return 1
    print(f"PASS: Amazon RVS Cloud Sandbox verified the {args.phase} for {SKU}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
