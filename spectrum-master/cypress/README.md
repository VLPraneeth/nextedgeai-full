## Run cypress in dev mode

`npm run cypress:localhost:open -- --env username=REPLACE_ME,password=REPLACE_ME`
This command uses the default username and password.


## Running cypress against an external server.
`CYPRESS_BASE_URL=http://spectrum.integration.syncari.net npx cypress run --browser chrome --env username=REPLACE_ME,password=REPLACE_ME`


## Running cypress against an external server headless
Note: username and password is required. See above external server example.
`CYPRESS_BASE_URL=http://spectrum.integration.syncari.net npx cypress run --headless --browser chrome`
