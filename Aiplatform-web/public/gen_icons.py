import struct
import zlib

def create_png(size, filename):
    width = size
    height = size
    
    raw_data = b''
    for y in range(height):
        raw_data += b'\x00'
        for x in range(width):
            t = (x + y) / (2 * size)
            r = int(139 + (59 - 139) * t)
            g = int(92 + (130 - 92) * t)
            b = 246
            raw_data += bytes([r, g, b, 255])
    
    signature = b'\x89PNG\r\n\x1a\n'
    
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b'IHDR' + ihdr_data)
    ihdr_chunk = struct.pack('>I', 13) + b'IHDR' + ihdr_data + struct.pack('>I', ihdr_crc)
    
    compressed = zlib.compress(raw_data)
    idat_crc = zlib.crc32(b'IDAT' + compressed)
    idat_chunk = struct.pack('>I', len(compressed)) + b'IDAT' + compressed + struct.pack('>I', idat_crc)
    
    iend_crc = zlib.crc32(b'IEND')
    iend_chunk = struct.pack('>I', 0) + b'IEND' + struct.pack('>I', iend_crc)
    
    png_data = signature + ihdr_chunk + idat_chunk + iend_chunk
    
    with open(filename, 'wb') as f:
        f.write(png_data)
    print(f'Created {filename} ({size}x{size})')

if __name__ == '__main__':
    create_png(192, 'pwa-192x192.png')
    create_png(512, 'pwa-512x512.png')
    create_png(180, 'apple-touch-icon.png')
    print('All icons created!')
