import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

class DualStackHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

def run(port=3000):
    directory = os.path.dirname(os.path.abspath(__file__))
    os.chdir(directory)
    
    server_address = ('127.0.0.1', port)
    httpd = ThreadingHTTPServer(server_address, DualStackHandler)
    print(f"========================================================", flush=True)
    print(f"  GabAI SDO Valenzuela City Admin Portal Server", flush=True)
    print(f"========================================================", flush=True)
    print(f"  URL: http://127.0.0.1:{port}/index.html", flush=True)
    print(f"  Multi-threaded: YES", flush=True)
    print(f"========================================================", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...", flush=True)
        httpd.server_close()

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    run(port)
