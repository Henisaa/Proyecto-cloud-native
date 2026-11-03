"""Real HTTP/JWT regression checks for compose.yaml + tests/local/compose.jwt.yml.

The test issuer signs real RSA tokens. Never print tokens or disable JWT validation.
Run only against the disposable local development stack.
"""
import json
import time
import unittest
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode

WEB = 'http://127.0.0.1:4200'
CATALOG = 'http://127.0.0.1:18081'
SHIPMENTS = 'http://127.0.0.1:18085'


def http(base, path, token=None, method='GET', data=None):
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if data is not None:
        headers['Content-Type'] = 'application/json'
    request = Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
    try:
        response = urlopen(request, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        status = response.status
    try:
        return status, json.loads(raw)
    except ValueError:
        return status, raw


def session(role, **extra):
    status, payload = http('http://127.0.0.1:19000', '/session?' + urlencode(dict(role=role, **extra)))
    assert status == 200
    return payload['accessToken']


class LocalStackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.admin = session('ADMIN')
        cls.client = session('Cliente del dominio', oid='local-smoke-owner')
        cls.other = session('CLIENTE', oid='local-smoke-other')
        cls.auditor = session('AUDITOR')
        cls.operator = session('Operador del dominio')
        code = 'LOCAL-' + uuid.uuid4().hex[:12]
        cls.email = code.lower() + '@example.test'
        status, result = http(CATALOG, '/api/catalog/services', cls.admin, 'POST', {
            'code': code, 'name': 'Servicio local smoke', 'category': 'EXPRESS', 'basePrice': 1000, 'estimatedDeliveryHours': 6,
        })
        assert status == 201, (status, result)
        cls.service_id = result['data']['id']
        cls.code = result['data']['code']
        status, result = http(CATALOG, f'/api/catalog/services/{cls.service_id}/capacities', cls.admin, 'POST', {
            'zone': 'URBAN_CENTER', 'vehicleType': 'VAN', 'maxDailyPackages': 20, 'maxWeightKg': 1000, 'maxVolumeM3': 100,
        })
        assert status == 201, (status, result)
        status, result = http(WEB, '/api/bff/shipments', cls.client, 'POST', {
            'serviceId': cls.service_id, 'zone': 'URBAN_CENTER', 'weightKg': 1, 'volumeM3': 0.01, 'packagesCount': 1,
            'recipientName': 'Prueba local', 'recipientEmail': cls.email,
            'originAddress': 'Origen local', 'destinationAddress': 'Destino local',
        })
        assert status == 201, (status, result)
        cls.shipment = result

    def test_01_static_frontend_and_proxy(self):
        self.assertEqual(http(WEB, '/health'), (200, 'OK'))
        self.assertIn('RutaExpress Console', http(WEB, '/')[1])
        self.assertIn('authMode', http(WEB, '/config.js')[1])
        status, profile = http(WEB, '/api/bff/auth/me', self.operator)
        self.assertEqual(status, 200)
        self.assertEqual(profile['roles'], ['DESPACHADOR'])

    def test_02_invalid_jwt_and_unknown_role(self):
        for base, path in [(WEB, '/api/bff/shipments'), (CATALOG, '/api/catalog/services'), (SHIPMENTS, '/api/shipments')]:
            self.assertEqual(http(base, path)[0], 401)
            for invalid in [session('ADMIN', expired='1'), session('ADMIN', aud='wrong'), session('ADMIN', iss='https://wrong.invalid')]:
                self.assertEqual(http(base, path, invalid)[0], 401)
        unknown = session('INVENTADO')
        self.assertEqual(http(WEB, '/api/bff/auth/me', unknown)[1]['roles'], [])
        self.assertEqual(http(WEB, '/api/bff/dashboard', unknown)[0], 403)

    def test_03_client_ownership_and_catalog_projection(self):
        shipment_id = self.shipment['id']
        self.assertEqual(http(WEB, f'/api/bff/shipments/{shipment_id}', self.other)[0], 404)
        self.assertEqual(http(WEB, f'/api/bff/shipments/{shipment_id}', self.client)[0], 200)
        for path in [f'/api/catalog/services/{self.service_id}', f'/api/catalog/services/code/{self.code}']:
            status, response = http(CATALOG, path, self.client)
            self.assertEqual(status, 200)
            for field in ['capacities', 'totalDailyCapacity', 'totalBookedPackages', 'overallUtilizationPercentage']:
                self.assertNotIn(field, response['data'])
        self.assertEqual(http(CATALOG, f'/api/catalog/services/{self.service_id}/capacities', self.client)[0], 403)

    def test_04_auditor_has_only_readonly_timeline(self):
        for base, prefix in [(WEB, '/api/bff/shipments'), (SHIPMENTS, '/api/shipments')]:
            for suffix in ['', '/summary', '/' + self.shipment['id']]:
                self.assertEqual(http(base, prefix + suffix, self.auditor)[0], 403)
        status, reference = http(WEB, '/api/bff/audit/resolve/' + self.shipment['trackingCode'], self.auditor)
        self.assertEqual(status, 200)
        self.assertEqual(reference, {'id': self.shipment['id'], 'trackingCode': self.shipment['trackingCode']})
        path = f"/api/bff/shipments/{self.shipment['id']}/timeline"
        self.assertEqual(http(WEB, path, self.auditor)[0], 200)
        self.assertEqual(http(WEB, path, self.operator)[0], 403)
        self.assertEqual(http(WEB, path, self.client)[0], 403)

    def test_05_async_reservation_mail_and_kafka(self):
        shipment_id = self.shipment['id']
        status, response = http(WEB, f'/api/bff/shipments/{shipment_id}/status', self.operator, 'PUT', {'status': 'ACEPTADO'})
        self.assertEqual(status, 200, response)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            response = http(WEB, f'/api/bff/shipments/{shipment_id}', self.admin)[1]
            if response['capacityStatus'] == 'RESERVADA':
                break
            time.sleep(2)
        self.assertEqual(response['capacityStatus'], 'RESERVADA')
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            mail = http('http://127.0.0.1:8025', '/api/v1/search?' + urlencode({'query': 'to:' + self.email}))[1]
            timeline = http(WEB, f'/api/bff/shipments/{shipment_id}/timeline', self.auditor)[1]
            if mail.get('messages', 0) and any(e['source'] == 'KAFKA' for e in timeline['events']):
                break
            time.sleep(2)
        self.assertTrue(mail.get('messages', 0))
        self.assertTrue(any(e['source'] == 'KAFKA' for e in timeline['events']))
        self.assertEqual(http(WEB, '/api/bff/report/kpis', self.admin)[0], 200)


if __name__ == '__main__':
    unittest.main(verbosity=2)
