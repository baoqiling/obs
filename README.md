# obs

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                       | Description                       |
|--------------------------------------------|-----------------------------------|
| [Routing](https://start.ktor.io/p/routing) | Provides a structured routing DSL |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## OBS direct download

`GET /package/download` remains behind the existing OAuth session (the OAuth
provider can be backed by the enterprise WeChat login). After the user passes
that authentication, the service creates a short-lived OBS GET signature and
returns a `302` redirect to the signed OBS URL. The Ktor service does not call
`getObject` or proxy the file bytes; the browser/download client transfers the
file directly with OBS.

Configure the signing credentials in the runtime environment:

```text
OBS_ENDPOINT=obs.cn-north-4.myhuaweicloud.com
OBS_BUCKET_NAME=releases.bhxz
OBS_ACCESS_KEY=<access key>
OBS_SECRET_KEY=<secret key>
OBS_DOWNLOAD_URL_EXPIRES_SECONDS=600
```

`OBS_DOWNLOAD_URL_EXPIRES_SECONDS` is optional and is limited to seven days.
Keep the access key and secret key on the server. A public OBS object can also
be downloaded without this service using the bucket's native object URL, but a
private object requires a signed URL (or OBS credentials in the client).

`GET /package/files` lists files under the `bhapi/`, `center6/`, `center7/`,
and `subway/` prefixes in `OBS_BUCKET_NAME` (defaults to `releases.bhxz`),
without enterprise WeChat authentication. Directory marker objects and objects
outside those four prefixes are omitted. It returns one native OBS URL per
line, for example
`https://releases.bhxz.obs.cn-north-4.myhuaweicloud.com/path/to/file.zip`.
Because this endpoint is public, anyone who can reach the service can enumerate
the bucket's object names. The object URLs themselves only download objects if
the bucket permits public reads.

