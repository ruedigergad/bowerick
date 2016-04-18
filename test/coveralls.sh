COVERALLS_URL='https://coveralls.io/api/v1/jobs'
lein2 cloverage -e "bowerick.main" -e "bowerick.java-interfaces" -e "bowerick.JmsController" -o cov --coveralls
curl -F 'json_file=@cov/coveralls.json' "$COVERALLS_URL"

