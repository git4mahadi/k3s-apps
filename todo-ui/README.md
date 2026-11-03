

docker build -t todo-ui:v26.11.1 .

docker run --rm --name todo-app -d -p 8080:80 todo-ui:v26.11.1


docker build --pull=false -t todo-ui:v26.11.1 .

docker save -o todo-ui-v26.11.1.tar todo-ui:v26.11.1

sudo k3s ctr -n k8s.io images import todo-ui-v26.11.1.tar