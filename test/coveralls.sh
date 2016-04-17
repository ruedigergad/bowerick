COVERALLS_URL='https://coveralls.io/api/v1/jobs'
lein2 with-profile cloverage cloverage -e "bowerick.main" -e "bowerick.java-interfaces" -o cov --coveralls
curl -F 'json_file=@cov/coveralls.json' "$COVERALLS_URL"

