COVERALLS_URL='https://coveralls.io/api/v1/jobs'
lein cloverage -e "bowerick.JmsController" -e "bowerick.java-interfaces" -o cov --coveralls
curl -F 'json_file=@cov/coveralls.json' "$COVERALLS_URL"

