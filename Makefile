.PHONY: verify safety backend-test web-build app-test

verify: safety backend-test web-build app-test

safety:
	python3 scripts/check_repository.py

backend-test:
	mvn -B -f apps/server/pom.xml verify

web-build:
	cd apps/web && npm run typecheck
	cd apps/web && MULTIUSER_BUILD=1 npm run build

app-test:
	python3 -m unittest discover -s integrations/workbench/tests -v

