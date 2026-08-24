#!/usr/bin/env python3
"""
Streams a synthetic financial-transactions CSV to disk.

Rows are written one at a time via csv.writer directly to the output file
handle -- nothing is accumulated in a list or DataFrame, so memory usage
stays flat regardless of --rows. This mirrors the constraint the backend
ingestion pipeline has to honor, and gives us a real large file to test it
against.

Usage:
    python generate_csv.py --rows 1500000 --out transactions.csv
"""
import argparse
import csv
import random
import sys
from datetime import date, timedelta

CATEGORIES = [
    "Groceries", "Rent", "Salary", "Utilities", "Travel",
    "Entertainment", "Dining", "Healthcare", "Shopping",
    "Transfer", "Insurance", "Subscription", "Education", "Investment",
]

# categories that represent income vs. expense, so amount sign is realistic
INCOME_CATEGORIES = {"Salary", "Transfer", "Investment"}

DESCRIPTION_TEMPLATES = {
    "Groceries": ["Supermarket purchase", "Grocery store", "Farmers market", "Corner store"],
    "Rent": ["Monthly rent payment", "Apartment lease payment"],
    "Salary": ["Payroll deposit", "Monthly salary", "Freelance payment received"],
    "Utilities": ["Electricity bill", "Water bill", "Internet service", "Gas bill"],
    "Travel": ["Flight booking", "Hotel reservation", "Train ticket", "Car rental"],
    "Entertainment": ["Movie tickets", "Concert tickets", "Streaming service", "Video game purchase"],
    "Dining": ["Restaurant", "Coffee shop", "Fast food", "Food delivery"],
    "Healthcare": ["Pharmacy purchase", "Doctor visit copay", "Dental checkup", "Health insurance premium"],
    "Shopping": ["Online retailer order", "Clothing store", "Electronics purchase", "Department store"],
    "Transfer": ["Transfer from savings", "P2P payment received", "Bank transfer"],
    "Insurance": ["Auto insurance premium", "Home insurance premium", "Life insurance premium"],
    "Subscription": ["Software subscription", "Magazine subscription", "Gym membership"],
    "Education": ["Tuition payment", "Textbook purchase", "Online course"],
    "Investment": ["Dividend payout", "Stock sale proceeds", "Interest income"],
}

START_DATE = date.today() - timedelta(days=365 * 2)
DATE_RANGE_DAYS = 365 * 2


def random_date():
    return START_DATE + timedelta(days=random.randint(0, DATE_RANGE_DAYS))


def random_amount(category):
    if category in INCOME_CATEGORIES:
        return round(random.uniform(50, 6000), 2)
    return -round(random.uniform(2, 3000), 2)


def generate(rows, out_path, progress_every, seed):
    if seed is not None:
        random.seed(seed)

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "date", "category", "amount", "description"])

        for i in range(1, rows + 1):
            category = random.choice(CATEGORIES)
            writer.writerow([
                i,
                random_date().isoformat(),
                category,
                random_amount(category),
                random.choice(DESCRIPTION_TEMPLATES[category]),
            ])

            if progress_every and i % progress_every == 0:
                print(f"  {i:,} / {rows:,} rows written", file=sys.stderr)

    print(f"Done. Wrote {rows:,} rows to {out_path}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=int, default=1_500_000, help="number of data rows to generate (default: 1,500,000)")
    parser.add_argument("--out", type=str, default="transactions.csv", help="output CSV path")
    parser.add_argument("--progress-every", type=int, default=100_000, help="print progress every N rows (0 to disable)")
    parser.add_argument("--seed", type=int, default=None, help="random seed for reproducible output")
    args = parser.parse_args()

    generate(args.rows, args.out, args.progress_every, args.seed)


if __name__ == "__main__":
    main()
