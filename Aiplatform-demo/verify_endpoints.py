import json
import re
import os

def extract_controller_endpoints(controller_path):
    """Extract all endpoints from a Spring Boot controller"""
    with open(controller_path, 'r') as f:
        content = f.read()
    
    # Find class-level @RequestMapping
    class_mapping = re.search(r'@RequestMapping\("([^"]+)"\)', content)
    base_path = class_mapping.group(1) if class_mapping else ''
    
    # Find all method-level mappings
    endpoints = []
    
    # Match @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @RequestMapping
    patterns = [
        (r'@GetMapping\("([^"]+)"\)', 'GET'),
        (r'@PostMapping\("([^"]+)"\)', 'POST'),
        (r'@PutMapping\("([^"]+)"\)', 'PUT'),
        (r'@DeleteMapping\("([^"]+)"\)', 'DELETE'),
        (r'@RequestMapping\(value\s*=\s*"([^"]+)"\s*,\s*method\s*=\s*RequestMethod\.(\w+)\)', None),
    ]
    
    for pattern, default_method in patterns:
        if default_method:
            for match in re.finditer(pattern, content):
                path = match.group(1)
                full_path = f'{base_path}{path}' if path.startswith('/') else f'{base_path}/{path}'
                endpoints.append((default_method, full_path))
        else:
            for match in re.finditer(pattern, content):
                path = match.group(1)
                method = match.group(2).upper()
                full_path = f'{base_path}{path}' if path.startswith('/') else f'{base_path}/{path}'
                endpoints.append((method, full_path))
    
    return endpoints

def extract_postman_endpoints(collection_path):
    """Extract all endpoints from a Postman collection"""
    with open(collection_path, 'r') as f:
        data = json.load(f)
    
    endpoints = []
    
    def process_items(items):
        for item in items:
            if 'request' in item:
                method = item['request']['method']
                url = item['request']['url']['raw']
                # Extract path from URL
                # Remove {{baseUrl}} prefix
                path = url.replace('{{baseUrl}}', '')
                endpoints.append((method, path))
            if 'item' in item:
                process_items(item['item'])
    
    process_items(data['item'])
    return endpoints

# Check AdminController
print('=== AdminController Endpoints ===')
admin_endpoints = extract_controller_endpoints('$HOME/Documents/Aiplatform/Aiplatform-demo/src/main/java/com/ai/gateway/controller/AdminController.java')
for method, path in sorted(admin_endpoints):
    print(f'{method} {path}')
print(f'Total: {len(admin_endpoints)}')

print('\n=== PackageAdminController Endpoints ===')
package_endpoints = extract_controller_endpoints('$HOME/Documents/Aiplatform/Aiplatform-demo/src/main/java/com/ai/gateway/controller/PackageAdminController.java')
for method, path in sorted(package_endpoints):
    print(f'{method} {path}')
print(f'Total: {len(package_endpoints)}')

print('\n=== ADMIN_API Postman Endpoints ===')
admin_postman = extract_postman_endpoints('$HOME/Documents/Aiplatform/Aiplatform-demo/ADMIN_API.postman_collection.json')
for method, path in sorted(admin_postman):
    print(f'{method} {path}')
print(f'Total: {len(admin_postman)}')
