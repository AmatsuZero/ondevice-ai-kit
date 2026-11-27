import sys
import json
import os

def load_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

from datetime import date

def normalize(data, packet):
    if data['version'] != 1 or type(data['version']) != int:
        raise ValueError('version')
    if data['kind'] != 'final':
        raise ValueError('kind')
    if data['packetId'] != packet['packetId']:
        raise ValueError('packetId')

    fields = data['fields']
    expected_fields = {'reviewer', 'date'}
    for item in packet['items']:
        qid = item['id']
        expected_fields.add(qid + '.note')
        for ab in ('A', 'B'):
            for suffix in ('p1', 'p2', 'q1', 'q2', 'veto', 'reason'):
                expected_fields.add(f'{qid}.{ab}.{suffix}')
    if not isinstance(fields, dict) or set(fields) != expected_fields or any(not isinstance(value, str) for value in fields.values()):
        raise ValueError('字段缺失、重复或类型错误')

    reviewer = fields['reviewer'].strip()
    if not reviewer:
        raise ValueError('reviewer')

    d = date.fromisoformat(fields['date'])
    if d.isoformat() != fields['date']:
        raise ValueError('date')

    result = {'reviewer': reviewer, 'date': fields['date'], 'answers': {}}

    for item in packet['items']:
        qid = item['id']
        for ab in 'AB':
            key = f'{qid}.{ab}'
            p1, p2 = fields[f'{key}.p1'], fields[f'{key}.p2']
            if p1 not in ('0', '1') or p2 not in ('0', '1'):
                raise ValueError(f'{key}.p1')
            veto = fields[f'{key}.veto']
            if veto not in ('yes', 'no'):
                raise ValueError(f'{key}.veto')
            reason = fields[f'{key}.reason']
            q1, q2 = fields[f'{key}.q1'], fields[f'{key}.q2']

            bits = [int(p1), int(p2)]
            quotes = [q1, q2]
            veto_bool = (veto == 'yes')

            if veto_bool and not reason.strip():
                raise ValueError(f'{key}.reason')

            if bits[0] == 1 and (not q1.strip() or q1.strip() not in item['answers'][ab]):
                raise ValueError(f'{key}.q1')
            if bits[1] == 1 and (not q2.strip() or q2.strip() not in item['answers'][ab]):
                raise ValueError(f'{key}.q2')

            score = 0 if veto_bool else sum(bits)
            result['answers'][f'{qid}.{ab}'] = {
                'bits': bits,
                'quotes': quotes,
                'veto': veto_bool,
                'score': score,
                'reason': reason,
                'note': fields[f'{qid}.note']
            }

    return result

def compare(first, second, packet, key):
    if first['reviewer'].strip().casefold() == second['reviewer'].strip().casefold():
        raise ValueError('需两位不同的评阅者')
    reviewers = [{'reviewer': r['reviewer'], 'date': r['date']} for r in (first, second)]
    totals = [{}, {}]
    point = veto = effective = n = 0
    disputes = []
    for item in packet['items']:
        rid = item['id']
        meta = key[rid]
        for ab in ('A', 'B'):
            k = rid + '.' + ab
            a = first['answers'][k]
            b = second['answers'][k]
            model = meta['models'][ab]
            group_model = meta['group'] + '/' + model
            for table, answer in zip(totals, (a, b)):
                row = table.setdefault(group_model, {'score': 0, 'max': 0})
                row['score'] += answer['score']
                row['max'] += 2
            n += 1
            point += sum(x == y for x, y in zip(a['bits'], b['bits']))
            veto += a['veto'] == b['veto']
            effective += a['score'] == b['score']
            if a['bits'] != b['bits'] or a['veto'] != b['veto']:
                disputes.append({
                    'key': k,
                    'originalId': meta['id'],
                    'model': model,
                    'question': item['question'],
                    'rawAnswer': item['answers'][ab],
                    'first': a,
                    'second': b
                })
    return {
        'reviewers': reviewers,
        'totals': totals,
        'agreement': {
            'point': [point, 2 * n],
            'veto': [veto, n],
            'effective': [effective, n]
        },
        'disputes': disputes
    }

def load_packet_and_key():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    packet_path = os.path.join(script_dir, '..', 'review', 'packet.json')
    key_path = os.path.join(script_dir, 'key.json')

    packet = load_json(packet_path)
    key = load_json(key_path)

    if not isinstance(packet, dict) or 'items' not in packet:
        raise ValueError("Invalid packet.json structure")
    if not isinstance(key, dict):
        raise ValueError("Invalid key.json structure")

    return packet, key


def main():
    if len(sys.argv) != 3:
        print("Usage: python3 compare.py FIRST.json SECOND.json", file=sys.stderr)
        sys.exit(1)

    try:
        first_path = sys.argv[1]
        second_path = sys.argv[2]

        first = load_json(first_path)
        second = load_json(second_path)

        # Validate inputs
        if not isinstance(first, dict) or 'fields' not in first:
            raise ValueError("Invalid first input")
        if not isinstance(second, dict) or 'fields' not in second:
            raise ValueError("Invalid second input")


        # Load packet and key
        packet, key = load_packet_and_key()

        # Compare
        result = compare(normalize(first, packet), normalize(second, packet), packet, key)

        print(json.dumps(result, ensure_ascii=False, indent=2))

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()
