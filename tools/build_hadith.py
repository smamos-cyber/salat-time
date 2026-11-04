import json, re, sys
src, out = sys.argv[1], sys.argv[2]
def load(n): return {h['hadithnumber']: h for h in json.load(open(f'{src}/editions/{n}.min.json'))['hadiths']}
PICKS = {
 'bukhari': [38,42,55,552,662,709,1032,1302,1426,1898,1903,2008,2446,2448,2472,2707,3257,4836,4937,5028,5304,5986,6011,6012,6013,6018,6021,6035,6114,6116,6168,6236,6237,6307,6340,6389,6405,6406,6446,6474,6484,6490,6508,7376,7422,7498,7563],
 'muslim': [136,154,160,161,170,172,267,912,1083,1084,1524,1768,2328,2755,2762,6501,6514,6526,6543,6548,6552,6563,6592,6598,6690,6811,6858,6876,6925,6927,7113],
}
NAMES = {'bukhari': 'Sahih al-Bukhari', 'muslim': 'Sahih Muslim', 'nawawi': 'Les 40 hadiths an-Nawawi', 'qudsi': 'Hadith qudsi (40 hadiths)'}
items = []
def num(h):
    a = h.get('arabicnumber'); 
    return str(int(float(a))) if a else str(h['hadithnumber'])
for col in ['nawawi', 'qudsi']:
    a, f = load('ara-' + col), load('fra-' + col)
    for k in sorted(f):
        if k in a and len(f[k]['text']) <= 700 and len(a[k]['text']) <= 700:
            items.append({'ar': a[k]['text'].strip(), 'fr': f[k]['text'].strip(), 'ref': f"{NAMES[col]}, n° {k}"})
for col, ks in PICKS.items():
    a, f = load('ara-' + col), load('fra-' + col)
    for k in ks:
        fr = f[k]['text'].strip(); ar = a[k]['text'].strip()
        assert fr and ar, (col, k)
        items.append({'ar': ar, 'fr': fr, 'ref': f"{NAMES[col]}, n° {num(f[k])}"})
for it in items:
    for key in ('ar', 'fr'):
        it[key] = re.sub(r'[‏‎]', '', it[key]); it[key] = re.sub(r'\s+', ' ', it[key]).strip()
json.dump({'source': 'fawazahmed0/hadith-api (domaine public)', 'hadiths': items}, open(out, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(items))
