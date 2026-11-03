"""Validate the local startup and security contract from `docker compose config --format json`."""
import json
import sys


def verify(config):
    services = config['services']
    backends = {'bff', 'catalog', 'shipments', 'notify', 'audit', 'report', 'rabbit-admin', 'kafka-admin'}
    for name in backends:
        service = services[name]
        assert service['environment']['SECURITY_ENABLED'] == 'true', f'{name}: JWT must remain enabled'
        assert not service.get('ports'), f'{name}: backend must remain internal'
        assert 'build' in service and 'healthcheck' in service, f'{name}: missing local build or healthcheck'

    for name, service in services.items():
        for dependency, options in service.get('depends_on', {}).items():
            expected = 'service_healthy'
            if dependency == 'rabbit-init':
                expected = 'service_completed_successfully'
            elif (name, dependency) in {('rabbit2', 'rabbit1'), ('notify', 'mailpit')}:
                expected = 'service_started'
            assert options['condition'] == expected, f'{name} -> {dependency}: expected {expected}'
    assert set(services['bff']['depends_on']) == backends - {'bff'}
    assert services['frontend']['depends_on']['bff']['condition'] == 'service_healthy'
    for name in ['shipments', 'catalog', 'audit', 'report']:
        password = services[name]['environment']['SPRING_DATASOURCE_PASSWORD']
        assert password == services['oracle']['environment'][name.upper() + '_DB_PASSWORD'], f'{name}: Oracle password mismatch'
    for service in services.values():
        for port in service.get('ports', []):
            assert port['host_ip'] == '127.0.0.1', 'local port must bind to loopback'


if __name__ == '__main__':
    verify(json.load(sys.stdin))
    print('Local Compose startup, isolation and JWT contract passed')
