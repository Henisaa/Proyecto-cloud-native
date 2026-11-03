"""Ephemeral issuer for CI only. Production never loads this module or its keys."""
import base64
import json
import os
import subprocess
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ISSUER = 'http://oidc:9000'
AUDIENCE = 'api://rutaexpress-ci'
KEY = '/tmp/ci-private.pem'
PUBLIC = '/tmp/ci-public.pem'
subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', KEY], check=True, capture_output=True)
subprocess.run(['openssl', 'pkey', '-in', KEY, '-pubout', '-out', PUBLIC], check=True, capture_output=True)
modulus = subprocess.check_output(['openssl', 'rsa', '-in', KEY, '-modulus', '-noout']).decode().strip().split('=')[1]


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()


def unb64(data):
    return base64.urlsafe_b64decode(data + '=' * (-len(data) % 4))


def token(role='ADMIN', oid=None, **options):
    now = int(time.time())
    claims = dict(iss=options.get('iss', ISSUER), aud=options.get('aud', AUDIENCE),
                  sub=oid or role, oid=oid or f'ci-{role.lower()}',
                  preferred_username=f'{role.lower()}@ci.example', name=f'CI {role}',
                  roles=[] if role == 'NONE' else [role], scp='access_as_user',
                  iat=now, nbf=now - 5, exp=now - 3600 if options.get('expired') else now + 3600)
    head = b64(json.dumps(dict(alg='RS256', typ='JWT', kid='ci-key')).encode())
    payload = b64(json.dumps(claims).encode())
    content = f'{head}.{payload}'.encode()
    signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', KEY], input=content, capture_output=True, check=True).stdout
    return f'{head}.{payload}.{b64(signature)}', claims


def verify(raw):
    try:
        head, payload, signature = raw.removeprefix('Bearer ').split('.')
        header, claims = json.loads(unb64(head)), json.loads(unb64(payload))
        if header.get('alg') != 'RS256' or claims.get('iss') != ISSUER or claims.get('aud') != AUDIENCE:
            return False
        now = time.time()
        if claims['exp'] <= now or claims.get('nbf', 0) > now:
            return False
        with tempfile.NamedTemporaryFile() as tmp:
            tmp.write(unb64(signature))
            tmp.flush()
            result = subprocess.run(['openssl', 'dgst', '-sha256', '-verify', PUBLIC, '-signature', tmp.name],
                                    input=f'{head}.{payload}'.encode(), capture_output=True)
        return result.returncode == 0
    except (ValueError, KeyError, TypeError):
        return False


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlparse(self.path)
        if path.path == '/verify':
            self.send_response(204 if verify(self.headers.get('Authorization', '')) else 401)
            self.end_headers()
            return
        if path.path == '/.well-known/openid-configuration':
            data = dict(issuer=ISSUER, jwks_uri=f'{ISSUER}/jwks', authorization_endpoint=f'{ISSUER}/authorize',
                        token_endpoint=f'{ISSUER}/token', response_types_supported=['code'],
                        subject_types_supported=['public'], id_token_signing_alg_values_supported=['RS256'])
        elif path.path == '/jwks':
            data = {'keys': [dict(kty='RSA', use='sig', alg='RS256', kid='ci-key', n=b64(bytes.fromhex(modulus)), e='AQAB')]}
        elif path.path == '/session':
            query = {k: v[0] for k, v in parse_qs(path.query).items()}
            raw, claims = token(**query)
            data = dict(accessToken=raw, claims=claims)
        elif path.path == '/health':
            data = {'status': 'UP'}
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, *_):
        pass  # Do not log tokens or Authorization headers.


ThreadingHTTPServer(('0.0.0.0', 9000), Handler).serve_forever()
