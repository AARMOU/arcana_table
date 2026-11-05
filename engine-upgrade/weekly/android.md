### Engine on Android (minSdk 26)

✅ The new engine is safe for the app: Java 17 bytecode, every Java API it calls exists on Android 26, and the app ships the library versions it was built with.

<details><summary>26 calls not in Android 26's API that the shipped engine already makes (not new; not blocking)</summary>

- `java.net.http.HttpClient`
- `java.net.http.HttpClient$Builder`
- `java.net.http.HttpClient$Builder.build()Ljava/net/http/HttpClient;`
- `java.net.http.HttpClient$Builder.connectTimeout(Ljava/time/Duration;)Ljava/net/http/HttpClient$Builder;`
- `java.net.http.HttpClient.newBuilder()Ljava/net/http/HttpClient$Builder;`
- `java.net.http.HttpClient.send(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;`
- `java.net.http.HttpRequest`
- `java.net.http.HttpRequest$BodyPublisher`
- `java.net.http.HttpRequest$BodyPublishers`
- `java.net.http.HttpRequest$BodyPublishers.ofString(Ljava/lang/String;)Ljava/net/http/HttpRequest$BodyPublisher;`
- `java.net.http.HttpRequest$Builder`
- `java.net.http.HttpRequest$Builder.POST(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpRequest$Builder.build()Ljava/net/http/HttpRequest;`
- `java.net.http.HttpRequest$Builder.header(Ljava/lang/String;Ljava/lang/String;)Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpRequest$Builder.timeout(Ljava/time/Duration;)Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpRequest$Builder.uri(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpRequest.newBuilder()Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpRequest.newBuilder(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;`
- `java.net.http.HttpResponse`
- `java.net.http.HttpResponse$BodyHandler`
- `java.net.http.HttpResponse$BodyHandlers`
- `java.net.http.HttpResponse$BodyHandlers.ofString()Ljava/net/http/HttpResponse$BodyHandler;`
- `java.net.http.HttpResponse.body()Ljava/lang/Object;`
- `java.net.http.HttpResponse.statusCode()I`
- `java.nio.file.Files.readString(Ljava/nio/file/Path;)Ljava/lang/String;`
- `java.nio.file.Files.writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;`

</details>
