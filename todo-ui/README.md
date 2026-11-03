

docker build -t todo-ui:v26.11.1 .

docker run --rm --name todo-app -d -p 8080:80 todo-ui:v26.11.1