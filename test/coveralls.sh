COVERALLS_URL='https://coveralls.io/api/v1/jobs'
lein2 with-profile cloverage cloverage -e "bowerick.main" -o cov --coveralls
curl -F 'json_file=@cov/coveralls.json' "$COVERALLS_URL"

