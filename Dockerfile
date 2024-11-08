FROM gcr.io/distroless/java21-debian12:nonroot

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

WORKDIR /app
COPY /app/build/libs/app-all.jar /app.jar

CMD ["app.jar"]
