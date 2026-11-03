# K3s on AWS EC2 Ubuntu 26.04: Build and Deploy Local Docker Images Without an Application Registry

**Updated:** 3 November 2026  
**Target:** Single AWS EC2 Ubuntu 26.04 LTS node  
**Application image distribution:** Local Docker build → tar archive → K3s containerd import → Kubernetes Deployment  
**Registry requirement:** No external or private application-image registry. Initial installation and base images still require an internet connection or pre-provisioned offline artifacts.

## 1. Architecture and prerequisites

```text
AWS EC2 (Ubuntu 26.04)
├── Docker Engine (build only)
│   └── docker build → docker save → myapp-v1.tar
└── K3s (Kubernetes with containerd)
    ├── k3s ctr -n k8s.io images import myapp-v1.tar
    ├── Deployment (image: myapp:v1; imagePullPolicy: Never)
    ├── Pod (port 8080)
    └── NodePort Service (port 30080)
```

Suggested starting instance: **2+ vCPU, 8+ GiB RAM, 40+ GiB gp3 EBS** (size up for Java workloads). In the AWS security group allow TCP 22 from your administrator IP and TCP 30080 from approved client IPs. Port 6443 need not be public if `kubectl` is used through SSH on the server.

> **Scope:** This is a **single-node** example. On a multi-node cluster the image must be imported onto every node eligible to run the Pod, or another supported distribution method must be used.

## 2. Update Ubuntu and install utilities

Connect to the server:

```bash
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Run:

```bash
cat /etc/os-release
uname -m
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git vim ca-certificates tar unzip
```

## 3. Install K3s

```bash
curl -sfL https://get.k3s.io | sh -
sudo systemctl is-active k3s
sudo k3s kubectl get nodes
sudo k3s kubectl get pods -A
```

Wait until the node reports `Ready` and essential system Pods are healthy. K3s bundles `kubectl`, `crictl`, and `ctr`.

## 4. **Important fix: `kubectl get nodes` says `permission denied` for K3s kubeconfig**

### 4.1 Observed error

```text
ubuntu@ip-172-31-46-149:~$ kubectl get nodes
WARN[0000] Unable to read /etc/rancher/k3s/k3s.yaml,
please start server with --write-kubeconfig-mode or
--write-kubeconfig-group to modify kube config permissions
error: error loading config file "/etc/rancher/k3s/k3s.yaml":
open /etc/rancher/k3s/k3s.yaml: permission denied
```

**Cause:** By default, the K3s-provided `kubectl` looks for `/etc/rancher/k3s/k3s.yaml` when `KUBECONFIG` is unset. That file is root-owned and normally mode `600`. Merely copying it to `~/.kube/config` does not necessarily change the file used by the K3s-provided `kubectl`.

### 4.2 Recommended fix for the `ubuntu` user

Run these commands **as the `ubuntu` user** (the `sudo install` operations safely copy the privileged config):

```bash
sudo install -d -o ubuntu -g ubuntu -m 700 /home/ubuntu/.kube
sudo install -o ubuntu -g ubuntu -m 600 \
  /etc/rancher/k3s/k3s.yaml \
  /home/ubuntu/.kube/config

export KUBECONFIG=/home/ubuntu/.kube/config
kubectl get nodes
```

**Make it persistent for future interactive Bash sessions:**

```bash
grep -qxF 'export KUBECONFIG=/home/ubuntu/.kube/config' ~/.bashrc || \
  echo 'export KUBECONFIG=/home/ubuntu/.kube/config' >> ~/.bashrc
source ~/.bashrc
```

**Verify which kubeconfig is in use:**

```bash
echo "$KUBECONFIG"
ls -l /home/ubuntu/.kube/config
kubectl config current-context
kubectl get nodes
```

**Immediate one-command test:**

```bash
KUBECONFIG=/home/ubuntu/.kube/config kubectl get nodes
```

Expected result: your EC2 node, such as `ip-172-31-46-149`, appears with `STATUS` of `Ready`.

**Alternative, without copying:**

```bash
sudo k3s kubectl get nodes
```

This works because `sudo` runs with root privileges. It does not solve access for ordinary `ubuntu` invocations.

**Security:** Do **not** run `chmod 777` or `chmod 644` on `/etc/rancher/k3s/k3s.yaml`; that admin credential grants full cluster privileges. The copied `/home/ubuntu/.kube/config` is also a highly privileged credential—protect it with `600`, and restrict access to the `ubuntu` account. K3s refreshes certificates in the original kubeconfig when it starts; copied kubeconfigs may need to be updated later.

### 4.3 If permission errors persist

```bash
whoami
command -v kubectl
ls -ld /home/ubuntu/.kube
ls -l /home/ubuntu/.kube/config
printf 'KUBECONFIG=%s\n' "$KUBECONFIG"
KUBECONFIG=/home/ubuntu/.kube/config kubectl get nodes
sudo systemctl status k3s --no-pager
```

Avoid splitting shell commands incorrectly. For instance, the entire `sudo cp ... destination` command must form one valid shell statement. A line-continuation backslash (`\`) must be the last character of a continued line (no extra text after it).

## 5. Install Docker Engine for local builds

For a simple Ubuntu environment:

```bash
sudo apt install -y docker.io
sudo systemctl enable --now docker
sudo docker version
```

Optional, for an administrator who should be allowed to run Docker without `sudo`:

```bash
sudo usermod -aG docker ubuntu
newgrp docker
docker version
```

> Membership in the Docker group is effectively root-equivalent. Restrict it to trusted administrators.

## 6. Prepare application files

This sample assumes a **Spring Boot JAR** named `myapp.jar` and a service listening on TCP **8080**.

```bash
sudo mkdir -p /opt/myapp/target /opt/myapp/k8s
sudo chown -R ubuntu:ubuntu /opt/myapp
cd /opt/myapp
```

Copy your compiled JAR to `/opt/myapp/target/myapp.jar`. If you have Maven sources, build the JAR using your normal project workflow and ensure the final filename matches.

Create `/opt/myapp/Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/myapp.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**No-registry rule for the base image:** If the EC2 host must not access Docker Hub, preload the matching Linux/architecture base image first from a trusted local image archive:

```bash
# Example only: base-image archive must already have been transferred to EC2.
docker load -i /opt/images/temurin-21-jre.tar
```

The actual archive filename may be anything; use, for example:

```bash
docker load -i /opt/images/temurin-21-jre.tar
docker image inspect eclipse-temurin:21-jre >/dev/null
```

Make sure the image's OS and architecture match the EC2 host. `docker build --pull=false` is **not** a substitute for having the base image available; otherwise a build may still attempt registry resolution or fail.

## 7. Build, export, and import the application image

```bash
cd /opt/myapp

docker build --pull=false -t myapp:v1 .
docker images | grep myapp

docker save -o myapp-v1.tar myapp:v1
ls -lh myapp-v1.tar

sudo k3s ctr -n k8s.io images import myapp-v1.tar
sudo k3s crictl images | grep myapp
sudo k3s ctr -n k8s.io images list | grep myapp
```

**Why import?** Docker's image store and K3s/containerd's image store are separate. The `-n k8s.io` containerd namespace matters for Kubernetes. Image names might be displayed in a normalized form such as `docker.io/library/myapp:v1`; this is not evidence of a registry pull.

## 8. Create Deployment and Service

Save the following as `/opt/myapp/k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  replicas: 1
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      containers:
        - name: myapp
          image: myapp:v1
          imagePullPolicy: Never
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "2"
              memory: 2Gi
---
apiVersion: v1
kind: Service
metadata:
  name: myapp-service
spec:
  type: NodePort
  selector:
    app: myapp
  ports:
    - name: http
      protocol: TCP
      port: 8080
      targetPort: 8080
      nodePort: 30080
```

`imagePullPolicy: Never` means that Kubernetes will not attempt to fetch this application image from a registry. The image must already be present in K3s containerd on the node where the Pod is scheduled.

## 9. Deploy and test

```bash
kubectl apply -f /opt/myapp/k8s/deployment.yaml
kubectl rollout status deployment/myapp --timeout=180s
kubectl get nodes
kubectl get deployments
kubectl get pods -o wide
kubectl get svc myapp-service
kubectl logs deployment/myapp --tail=100
```

From the EC2 host, test a route your application actually exposes:

```bash
curl -i http://127.0.0.1:30080/
# If Spring Boot Actuator exposes the health endpoint:
curl -i http://127.0.0.1:30080/actuator/health
```

From an allowed client, use:

```text
http://YOUR_EC2_PUBLIC_IP:30080
```

Ensure the security group allows inbound TCP **30080** from the client IP. An HTTP `404` can still demonstrate network reachability if your app does not implement `/`.

## 10. Release version v2 without pushing to a registry

After rebuilding or replacing `target/myapp.jar`:

```bash
cd /opt/myapp

docker build --pull=false -t myapp:v2 .
docker save -o myapp-v2.tar myapp:v2
sudo k3s ctr -n k8s.io images import myapp-v2.tar
sudo k3s crictl images | grep myapp

kubectl set image deployment/myapp myapp=myapp:v2
kubectl rollout status deployment/myapp --timeout=180s
kubectl get pods -o wide
```

Update `image: myapp:v2` in `k8s/deployment.yaml` too, so a subsequent `kubectl apply` does not revert the Deployment to v1. Use immutable release tags rather than overwriting `latest`.

## 11. Optional build-and-deploy script

Create `/opt/myapp/build-deploy.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

APP_NAME=myapp
VERSION="${1:?Usage: ./build-deploy.sh v2}"
IMAGE="${APP_NAME}:${VERSION}"
ARCHIVE="/tmp/${APP_NAME}-${VERSION}.tar"

cd /opt/myapp
docker build --pull=false -t "$IMAGE" .
docker save -o "$ARCHIVE" "$IMAGE"
sudo k3s ctr -n k8s.io images import "$ARCHIVE"
sudo k3s crictl images | grep "$APP_NAME"
kubectl set image "deployment/${APP_NAME}" "${APP_NAME}=${IMAGE}"
kubectl rollout status "deployment/${APP_NAME}" --timeout=180s
kubectl get pods -l "app=${APP_NAME}"
rm -f "$ARCHIVE"
echo "Deployed $IMAGE"
```

```bash
chmod +x /opt/myapp/build-deploy.sh
cd /opt/myapp
./build-deploy.sh v3
```

This script assumes the Kubernetes Deployment already exists. It only rebuilds the image from your existing files; compile your Java source first if the JAR needs refreshing. Keep the manifest synchronized with the deployed version.

## 12. Troubleshooting quick reference

| Symptom | Check / remedy |
|---|---|
| `permission denied: /etc/rancher/k3s/k3s.yaml` | Set `KUBECONFIG=/home/ubuntu/.kube/config` and verify ownership/mode (`600`). |
| `sudo: k3s: command not found` | Verify K3s installation and `/usr/local/bin/k3s`; do not assume a Docker-only host has K3s installed. |
| `ErrImageNeverPull` | Import the exact image/tag into the node's `k8s.io` containerd namespace. |
| `ImagePullBackOff` | Verify `imagePullPolicy: Never` and whether your manifest actually uses the intended image. |
| `CrashLoopBackOff` | Run `kubectl logs deployment/myapp --previous` (where applicable) and `kubectl describe pod POD_NAME`. |
| `Pending` | Check scheduler events and available CPU/memory with `kubectl describe pod POD_NAME`. |
| NodePort not reachable | Check service selectors, Pod readiness, EC2 security group, host firewall, application bind address/port. |
| Image exists in Docker but not K3s | Run `docker save`, then `sudo k3s ctr -n k8s.io images import ...`. |
| `exec format error` | Check image architecture vs EC2 (`uname -m` and `docker image inspect`). |

Useful diagnostic commands:

```bash
kubectl get events --sort-by=.lastTimestamp | tail -30
kubectl describe deployment myapp
kubectl describe pod POD_NAME
kubectl logs deployment/myapp --tail=100
sudo k3s crictl images
sudo journalctl -u k3s -n 100 --no-pager
```

## 13. Operational and security notes

1. A **no-application-registry** workflow is not necessarily a **fully air-gapped** installation. K3s installation, packaged system images, Docker packages, and the Dockerfile base image must be made available through initial network access or trusted offline transfers.
2. Never make the original admin kubeconfig world-readable. The locally copied file is equally sensitive. Update the copy when K3s regenerates the original certificates.
3. One EC2 node is a single point of failure. Multiple Pod replicas on one node do not give node-level high availability.
4. K3s can keep imported images locally, but a rebuilt/replaced EC2 node will require those images to be imported again. Retain your release archives or a reproducible build process.
5. Use Kubernetes Secrets for sensitive values, appropriate resource limits for JVM processes, and persistent storage/backups for stateful components.
6. For a multi-node future, every schedulable node needs the image; consider a private in-cluster distribution mechanism only if the topology grows beyond this single-node plan.

## 14. Condensed commands

```bash
# Fix kubeconfig (run as ubuntu)
sudo install -d -o ubuntu -g ubuntu -m 700 /home/ubuntu/.kube
sudo install -o ubuntu -g ubuntu -m 600 /etc/rancher/k3s/k3s.yaml /home/ubuntu/.kube/config
export KUBECONFIG=/home/ubuntu/.kube/config
kubectl get nodes

# Build locally; base image must already be available
cd /opt/myapp
docker build --pull=false -t myapp:v1 .
docker save -o myapp-v1.tar myapp:v1

# Transfer image from Docker to K3s
sudo k3s ctr -n k8s.io images import myapp-v1.tar
sudo k3s crictl images | grep myapp

# Run in Kubernetes
kubectl apply -f k8s/deployment.yaml
kubectl rollout status deployment/myapp
kubectl get pods -o wide
kubectl get svc myapp-service
```

## Official documentation

- [K3s cluster access and kubeconfig](https://docs.k3s.io/cluster-access)
- [K3s bundled CLI tools](https://docs.k3s.io/cli)
- [K3s image import](https://docs.k3s.io/add-ons/import-images)
- [K3s air-gap installation](https://docs.k3s.io/installation/airgap)
- [K3s quick start](https://docs.k3s.io/quick-start)
