import json

with open('ADMIN_API.postman_collection.json') as f:
    admin_data = json.load(f)

with open('API_TEST.postman_collection.json') as f:
    api_data = json.load(f)

def count_items(items, prefix=''):
    results = []
    for item in items:
        if 'request' in item:
            method = item['request']['method']
            url = item['request']['url']['raw']
            results.append(f'{prefix}{method} {url}')
        if 'item' in item:
            results.extend(count_items(item['item'], prefix + '  '))
    return results

admin_items = count_items(admin_data['item'])
api_items = count_items(api_data['item'])

print('=== ADMIN_API Interfaces ===')
for item in admin_items:
    print(item)
print(f'\nTotal: {len(admin_items)}')

print('\n=== API_TEST Interfaces ===')
for item in api_items:
    print(item)
print(f'\nTotal: {len(api_items)}')
