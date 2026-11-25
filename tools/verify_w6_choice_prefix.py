"""Reject immediate final-choice scoring inside checkpoint reasoning tokens.

This is one necessary offline gate, not full tokenizer/model protocol parity.
Run before inference with the actual prepared tokens and checkpoint tokenizer.
"""
import argparse
import hashlib
import json
from pathlib import Path


def require_final_answer_prefix(tokens, think_start, think_end):
    last_start = max((i for i, token in enumerate(tokens) if token == think_start), default=-1)
    last_end = max((i for i, token in enumerate(tokens) if token == think_end), default=-1)
    if last_start > last_end:
        raise ValueError('Immediate final-choice grammar cannot consume an open reasoning prefix')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cases', type=Path)
    parser.add_argument('tokenizer', type=Path)
    args = parser.parse_args()
    raw_cases, raw_tokenizer = args.cases.read_bytes(), args.tokenizer.read_bytes()
    cases, tokenizer = json.loads(raw_cases), json.loads(raw_tokenizer)
    ids = {row['content']: row['id'] for row in tokenizer['added_tokens']}
    rejected = []
    for case in cases:
        try:
            require_final_answer_prefix(case['expectedTokens'], ids['<think>'], ids['</think>'])
        except ValueError as error:
            rejected.append({'caseId': case['caseId'], 'reason': str(error)})
    print(json.dumps({'state': 'rejected' if rejected else 'passed', 'checked': len(cases),
                      'rejected': rejected, 'casesSHA256': hashlib.sha256(raw_cases).hexdigest(),
                      'tokenizerSHA256': hashlib.sha256(raw_tokenizer).hexdigest(),
                      'scope': 'Necessary reasoning-prefix check only; no inference or full protocol acceptance'}, indent=2))
    return 1 if rejected else 0


if __name__ == '__main__':
    raise SystemExit(main())
