# Kubernetes Networking & Architecture Guide

## Table of Contents
1. [Kubernetes Installation](#kubernetes-installation)
2. [Architecture on Your Laptop](#architecture-on-your-laptop)
3. [How Deployment Works](#how-deployment-works)
4. [Kubernetes Networking](#kubernetes-networking)
5. [Linux Networking Basics](#linux-networking-basics)
6. [Kubernetes Routing](#kubernetes-routing)
7. [Services Explained](#services-explained)
8. [Service vs Routing Table](#service-vs-routing-table)

---

## Kubernetes Installation

### What Gets Installed

```
Your Laptop
├── Control Plane (Master)
│   ├── API Server
│   ├── Scheduler
│   ├── Controller Manager
│   ├── etcd (database)
│   └── kube-proxy
└── Worker Node (same machine)
    ├── kubelet
    ├── Container Runtime (Docker/containerd)
    └── kube-proxy
```

### During Installation: minikube start

```
minikube start
    ↓
Creates a VM (or uses Docker)
    ↓
Starts Control Plane:
├── etcd (database) - listens on port 2379
├── API Server - listens on port 6443
├── Scheduler - watches for unscheduled pods
├── Controller Manager - manages deployments, replicasets, etc.
└── kube-proxy - manages networking
    ↓
Starts Worker Node (same machine):
├── kubelet - communicates with API Server
├── Container Runtime (Docker) - runs containers
└── kube-proxy - manages service networking
    ↓
Creates Configuration Files:
~/.kube/config
├── cluster: kubernetes-admin@kubernetes
├── user: kubernetes-admin
├── certificate: client certificate
└── token: authentication token
```

### kubectl Can Now Connect

```
kubectl get nodes
    ↓
kubectl reads ~/.kube/config
    ↓
Connects to API Server (https://localhost:6443)
    ↓
API Server authenticates using certificate
    ↓
API Server returns list of nodes
```

---

## Architecture on Your Laptop

```
┌─────────────────────────────────────────────┐
│         Your Laptop (Single Machine)        │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │      Control Plane (Master)          │  │
│  ├──────────────────────────────────────┤  │
│  │ • API Server (port 6443)             │  │
│  │ • Scheduler                          │  │
│  │ • Controller Manager                 │  │
│  │ • etcd (port 2379)                   │  │
│  │ • kube-proxy                         │  │
│  └──────────────────────────────────────┘  │
│                    ↓                        │
│  ┌──────────────────────────────────────┐  │
│  │      Worker Node (same machine)      │  │
│  ├──────────────────────────────────────┤  │
│  │ • kubelet                            │  │
│  │ • Docker/containerd                  │  │
│  │ • kube-proxy                         │  │
│  │ • Your Pods/Containers               │  │
│  └──────────────────────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
```

### Key Differences: Laptop vs Production

| Aspect | Laptop (minikube) | Production |
|--------|-------------------|-----------|
| Control Plane | 1 machine | Multiple machines (HA) |
| Worker Nodes | 1 machine | Many machines |
| etcd | Single instance | Multiple instances (HA) |
| API Server | Single instance | Multiple instances (load balanced) |
| Networking | Simple (all local) | Complex (multi-machine) |
| Storage | Local disk | Persistent volumes (cloud/NAS) |

### Ports on Your Laptop

```
6443  - API Server
2379  - etcd
10250 - kubelet
10256 - kube-proxy
```

---

## How Deployment Works

### What Happens When You Deploy Something

```
kubectl create -f pod.yaml
    ↓
1. kubectl sends request to API Server (localhost:6443)
    ↓
2. API Server (running on your laptop)
   ├── Authenticates request
   ├── Validates pod definition
   └── Stores in etcd (also on your laptop)
    ↓
3. Scheduler (running on your laptop)
   ├── Watches for unscheduled pods
   ├── Sees your new pod
   ├── Finds available node (your laptop's worker node)
   └── Assigns pod to node
    ↓
4. kubelet (running on your laptop)
   ├── Watches for pods assigned to it
   ├── Sees your pod assignment
   ├── Pulls Docker image
   ├── Starts container
   └── Reports status back to API Server
    ↓
5. API Server updates etcd with pod status
    ↓
6. kubectl watches for status changes
   └── Shows "Running" when container starts
```

### Real Example: Step by Step

**Step 1: Install Kubernetes**
```bash
minikube start
# Creates VM with Control Plane and Worker Node
# This is ONE-TIME setup
```

**Step 2: Create pod.yaml**
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-nginx
spec:
  containers:
  - name: nginx
    image: nginx:latest
```

**Step 3: Apply YAML**
```bash
kubectl apply -f pod.yaml
```

**Step 4: Check pod**
```bash
kubectl get pods
# NAME       READY   STATUS    RESTARTS   AGE
# my-nginx   1/1     Running   0          10s
# The pod is running on the existing worker node
# No new nodes were created
```

---

## Kubernetes Networking

### Docker Workflow vs Kubernetes Workflow

#### Docker Workflow
```
Dockerfile
    ↓
docker build
    ↓
Docker Image created
    ↓
docker run (or docker-compose.yml)
    ↓
Docker daemon reads config
    ↓
Container created under Docker bridge
    ↓
Container runs
```

#### Kubernetes Workflow
```
pod.yaml (or deployment.yaml)
    ↓
kubectl apply -f pod.yaml
    ↓
API Server reads YAML
    ↓
Stores in etcd
    ↓
Scheduler assigns to node
    ↓
kubelet on node pulls image
    ↓
Container created (using Docker/containerd)
    ↓
Pod runs
```

### Two Types of Requests

```
1. API Server Requests (kubectl, controllers)
   └── Always go through API Server (port 6443)

2. Application Requests (users calling /getUsers)
   └── Go directly to pods, NOT through API Server
```

#### Request Type 1: API Server Requests (Management)

```
kubectl apply -f deployment.yaml
    ↓
Request goes to API Server (Master)
    ↓
API Server processes
    ↓
API Server stores in etcd
    ↓
Controllers react to changes
```

**Who uses API Server:**
- kubectl commands
- Scheduler
- Controller Manager
- kubelet (reports status)
- All management operations

#### Request Type 2: Application Requests (User Traffic)

```
Browser: GET http://192.168.1.2:30008/getUsers
    ↓
Request goes DIRECTLY to pod
    ↓
Does NOT go through API Server
    ↓
Pod processes request
    ↓
Returns response
```

**Who uses this:**
- End users
- External clients
- Other applications
- All user traffic

### Your Machine: Two Different Ports

```
Your Machine (192.168.1.2)

Port 6443 (API Server - Master)
    ↑
    └── kubectl commands
    └── Controller Manager
    └── Scheduler
    └── Management operations

Port 30008 (Service - Worker)
    ↑
    └── User requests
    └── Browser calls
    └── Application traffic
```

### Complete Picture: Both Request Types

```
┌─────────────────────────────────────────────────────┐
│         Your Machine (192.168.1.2)                  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │        Control Plane (Master)                │  │
│  │        Port 6443                             │  │
│  ├──────────────────────────────────────────────┤  │
│  │ • API Server                                 │  │
│  │ • Scheduler                                  │  │
│  │ • Controller Manager                         │  │
│  │ • etcd                                       │  │
│  └──────────────────────────────────────────────┘  │
│           ↑                                         │
│           │ API Server Requests                    │
│           │ (kubectl, controllers)                 │
│           │                                         │
│  ┌────────┴──────────────────────────────────────┐ │
│  │        Worker Node                            │ │
│  │        Port 30008                             │ │
│  ├──────────────────────────────────────────────┤ │
│  │ • kubelet                                    │ │
│  │ • kube-proxy                                 │ │
│  │ • Container runtime (Docker)                 │ │
│  ├──────────────────────────────────────────────┤ │
│  │ Pods:                                        │ │
│  │ • Pod 1 (user-service) - 10.0.0.5:8080      │ │
│  │ • Pod 2 (user-service) - 10.0.0.6:8080      │ │
│  │ • Pod 3 (user-service) - 10.0.0.7:8080      │ │
│  └──────────────────────────────────────────────┘ │
│           ↑                                         │
│           │ Application Requests                   │
│           │ (user traffic)                         │
│           │                                         │
└───────────┼─────────────────────────────────────────┘
            │
    ┌───────┴──────────┐
    │                  │
Another Machine    Browser
(192.168.1.5)      (192.168.1.5)
```

---

## Linux Networking Basics

### Scenario 1: Two Machines on Same Network

```
Machine A (192.168.1.2)          Machine B (192.168.1.3)
├── Network Interface: eth0      ├── Network Interface: eth0
│   IP: 192.168.1.2              │   IP: 192.168.1.3
└── Routing Table                └── Routing Table
    ├── 192.168.1.0/24 → eth0        ├── 192.168.1.0/24 → eth0
    └── default → gateway            └── default → gateway

When Machine A sends to Machine B:
Machine A: ping 192.168.1.3
    ↓
1. Check routing table: "Where is 192.168.1.3?"
    ↓
2. Routing table says: "192.168.1.0/24 is on eth0"
    ↓
3. Send packet out eth0
    ↓
4. Switch/Network delivers to Machine B
    ↓
5. Machine B receives on eth0

Routing table entry:
Destination    Gateway    Interface
192.168.1.0/24 0.0.0.0    eth0
```

### Scenario 2: Containers on Same Machine

```
Machine A (192.168.1.2)
├── Network Interface: eth0 (192.168.1.2)
├── Docker Bridge: docker0 (172.17.0.1)
│   ├── Container 1: 172.17.0.2
│   └── Container 2: 172.17.0.3
└── Routing Table
    ├── 192.168.1.0/24 → eth0
    ├── 172.17.0.0/16 → docker0
    └── default → gateway

When Container 1 sends to Container 2:
Container 1 (172.17.0.2): ping 172.17.0.3
    ↓
1. Container checks routing table: "Where is 172.17.0.3?"
    ↓
2. Routing table says: "172.17.0.0/16 is on docker0"
    ↓
3. Send packet to docker0 bridge
    ↓
4. docker0 bridge delivers to Container 2
    ↓
5. Container 2 receives

Routing table entry:
Destination    Gateway    Interface
172.17.0.0/16  0.0.0.0    docker0
```

---

## Kubernetes Routing

### Kubernetes Network Architecture

```
Machine (192.168.1.2)
├── Network Interface: eth0 (192.168.1.2)
├── Pod Network: 10.0.0.0/24 (virtual)
│   ├── Pod 1: 10.0.0.5
│   ├── Pod 2: 10.0.0.6
│   └── Pod 3: 10.0.0.7
└── Routing Table
    ├── 192.168.1.0/24 → eth0
    ├── 10.0.0.0/24 → cni0 (Container Network Interface)
    └── default → gateway

Key Difference from Docker:
- Docker uses docker0 bridge
- Kubernetes uses cni0 (Container Network Interface)
- Both work similarly
```

### How Kubernetes Creates Routing

**Step 1: Pod Creation**
```
kubectl apply -f deployment.yaml
    ↓
Kubernetes creates pod
    ↓
kubelet receives: "Create pod 10.0.0.5"
    ↓
kubelet calls CNI plugin (e.g., flannel, weave, calico)
    ↓
CNI plugin:
├── Creates virtual network interface
├── Assigns IP 10.0.0.5
├── Connects to cni0 bridge
└── Updates routing table
```

**Step 2: Routing Table Updated**
```
Before pod creation:
Destination    Gateway    Interface
192.168.1.0/24 0.0.0.0    eth0
default        192.168.1.1 eth0

After pod creation:
Destination    Gateway    Interface
192.168.1.0/24 0.0.0.0    eth0
10.0.0.0/24    0.0.0.0    cni0
default        192.168.1.1 eth0

New entry: "To reach 10.0.0.0/24, send to cni0 bridge"
```

**Step 3: Service Creation**
```
kubectl apply -f service.yaml
    ↓
Service created with IP 10.96.0.1 (ClusterIP)
    ↓
kube-proxy watches for service
    ↓
kube-proxy creates iptables rules
```

### How Traffic Routes (Detailed)

```
Browser: http://192.168.1.2:30008/getUsers

1. Request arrives at your machine port 30008
    ↓
2. Kernel checks: "Is port 30008 listening?"
   Yes, kube-proxy created a rule for it
    ↓
3. iptables rule matches
   "Destination is 192.168.1.2:30008"
   "This matches service user-service"
   "Service has endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]"
    ↓
4. Load balancing happens
   "Send to 10.0.0.5:8080" (round-robin)
    ↓
5. Packet rewritten by iptables
   Destination: 192.168.1.2:30008 → 10.0.0.5:8080
    ↓
6. Kernel checks routing table:
   "Where is 10.0.0.5?"
   "10.0.0.0/24 is on cni0"
    ↓
7. Packet sent to cni0 bridge
    ↓
8. cni0 bridge delivers to Pod 1 (10.0.0.5)
    ↓
9. Pod receives request
    ↓
10. Spring Boot processes request
    ↓
11. Response sent back
    ↓
12. iptables reverse NAT:
    Rewrite source: 10.0.0.5 → 192.168.1.2
    ↓
13. Response reaches browser
```

### iptables Rules (The Magic)

iptables is a Linux firewall/routing tool that:
- Intercepts packets
- Applies rules
- Modifies packets (NAT - Network Address Translation)
- Routes packets

**Example iptables Rule:**
```
Chain PREROUTING (policy ACCEPT)
target     prot opt source      destination
KUBE-SERVICES all  --  0.0.0.0/0  0.0.0.0/0

Chain KUBE-SERVICES (2 references)
target     prot opt source      destination
KUBE-SVC-XXXX tcp  --  0.0.0.0/0  10.96.0.1    tcp dpt:8080
KUBE-SVC-YYYY tcp  --  0.0.0.0/0  0.0.0.0/0    tcp dpt:30008

Chain KUBE-SVC-YYYY (1 references)
target     prot opt source      destination
KUBE-SEP-1 all  --  0.0.0.0/0  0.0.0.0/0    statistic mode random probability 0.33
KUBE-SEP-2 all  --  0.0.0.0/0  0.0.0.0/0    statistic mode random probability 0.50
KUBE-SEP-3 all  --  0.0.0.0/0  0.0.0.0/0

Chain KUBE-SEP-1 (1 references)
target     prot opt source      destination
DNAT       tcp  --  0.0.0.0/0  0.0.0.0/0    tcp to:10.0.0.5:8080

Chain KUBE-SEP-2 (1 references)
target     prot opt source      destination
DNAT       tcp  --  0.0.0.0/0  0.0.0.0/0    tcp to:10.0.0.6:8080

Chain KUBE-SEP-3 (1 references)
target     prot opt source      destination
DNAT       tcp  --  0.0.0.0/0  0.0.0.0/0    tcp to:10.0.0.7:8080
```

**What this means:**
```
Rule 1: If destination is 10.96.0.1:8080 → go to KUBE-SVC-XXXX
Rule 2: If destination is 0.0.0.0:30008 → go to KUBE-SVC-YYYY

KUBE-SVC-YYYY has 3 sub-rules:
- 33% probability → KUBE-SEP-1 → DNAT to 10.0.0.5:8080
- 50% probability → KUBE-SEP-2 → DNAT to 10.0.0.6:8080
- 100% probability → KUBE-SEP-3 → DNAT to 10.0.0.7:8080

Result: Load balancing across 3 pods!
```

### How kube-proxy Creates These Rules

```
kube-proxy starts
    ↓
Watches API Server for services
    ↓
Sees: Service "user-service" with 3 endpoints
    ↓
Creates iptables rules:
├── Rule for ClusterIP (10.96.0.1)
├── Rule for NodePort (30008)
└── Load balance to 3 endpoints
    ↓
Watches for changes
    ↓
If pod added/removed:
├── Update endpoints
├── Update iptables rules
└── Load balancing automatically adjusted
```

### Network Diagram with Routing

```
External Request (192.168.1.5)
    ↓
    GET http://192.168.1.2:30008/getUsers
    ↓
┌───────────────────────────────────────────────────────┐
│ Your Machine (192.168.1.2)                            │
├───────────────────────────────────────────────────────┤
│                                                       │
│ eth0 (192.168.1.2)                                   │
│   ↓                                                   │
│ Kernel: "Port 30008 for kube-proxy"                  │
│   ↓                                                   │
│ kube-proxy (iptables rules)                          │
│   ↓                                                   │
│ iptables PREROUTING:                                 │
│   "30008 → 10.0.0.5:8080" (DNAT)                    │
│   ↓                                                   │
│ Routing Table:                                        │
│   "10.0.0.0/24 → cni0"                              │
│   ↓                                                   │
│ cni0 bridge                                           │
│   ↓                                                   │
│ ┌─────────────────────────────────────────────────┐ │
│ │ Pod Network (10.0.0.0/24)                       │ │
│ ├─────────────────────────────────────────────────┤ │
│ │ Pod 1 (10.0.0.5:8080) ← Request arrives here   │ │
│ │ Pod 2 (10.0.0.6:8080)                           │ │
│ │ Pod 3 (10.0.0.7:8080)                           │ │
│ └─────────────────────────────────────────────────┘ │
│   ↓                                                   │
│ Pod 1 processes request                              │
│   ↓                                                   │
│ Response: Source 10.0.0.5, Dest 192.168.1.5         │
│   ↓                                                   │
│ iptables POSTROUTING:                                │
│   "10.0.0.5 → 192.168.1.2" (SNAT)                  │
│   ↓                                                   │
│ eth0 sends response                                  │
│   ↓                                                   │
└───────────────────────────────────────────────────────┘
    ↓
Response arrives at 192.168.1.5
```

### Key Concepts Summary

```
1. Routing Table
   └── Tells kernel: "To reach X, use interface Y"

2. iptables Rules
   └── Intercepts packets and modifies them (NAT)

3. CNI (Container Network Interface)
   └── Plugin that creates pod network and routing

4. kube-proxy
   └── Watches API Server and creates iptables rules

5. Service
   └── Virtual IP that load balances to pods

6. Endpoints
   └── List of pod IPs behind a service

7. Load Balancing
   └── iptables rules distribute traffic across pods
```

---

## Services Explained

### What is a Service?

A Service is a **virtual load balancer** that:
- Provides a stable IP address
- Routes traffic to pods
- Load balances across multiple pods
- Abstracts pod IPs (which change)

### Problem Without Service

```
You have 3 pods:
├── Pod 1: 10.0.0.5
├── Pod 2: 10.0.0.6
└── Pod 3: 10.0.0.7

Problem 1: Pod IPs are temporary
├── If pod crashes, new pod gets new IP
├── If you hardcode 10.0.0.5, it breaks
└── You need to update config every time

Problem 2: No load balancing
├── How do you distribute traffic?
├── Which pod should receive request?
└── Manual routing is complex

Problem 3: No service discovery
├── How do other apps find your pods?
├── They need to know all pod IPs
└── Maintenance nightmare
```

### Solution: Service

```
Service "user-service"
├── Virtual IP: 10.96.0.1 (stable, doesn't change)
├── Port: 8080
├── Endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]
└── Load balancing: Distributes traffic

Benefits:
├── Stable IP (doesn't change when pods restart)
├── Automatic load balancing
├── Service discovery (DNS: user-service)
└── Pod IPs are hidden
```

### Service YAML Definition

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  type: ClusterIP              # Type of service
  selector:
    app: user-service          # Select pods with this label
  ports:
  - port: 8080                 # Service port
    targetPort: 8080           # Pod port
    nodePort: 30008            # External port (if NodePort)
```

### How Service Works

**Step 1: Service Creation**
```bash
kubectl apply -f service.yaml
    ↓
Kubernetes creates Service object
    ↓
Service gets IP: 10.96.0.1
    ↓
Service stores in etcd
```

**Step 2: kube-proxy Watches**
```
kube-proxy watches API Server:
    "Give me all services"
    ↓
API Server returns:
    Service "user-service"
    ├── IP: 10.96.0.1
    ├── Port: 8080
    └── Selector: app=user-service
    ↓
kube-proxy finds endpoints:
    "Find all pods with label app=user-service"
    ↓
API Server returns:
    ├── Pod 1: 10.0.0.5
    ├── Pod 2: 10.0.0.6
    └── Pod 3: 10.0.0.7
```

**Step 3: kube-proxy Creates iptables Rules**
```
kube-proxy creates rules:

Rule 1: ClusterIP
    If destination is 10.96.0.1:8080
    Load balance to [10.0.0.5:8080, 10.0.0.6:8080, 10.0.0.7:8080]

Rule 2: NodePort
    If destination is 0.0.0.0:30008
    Load balance to [10.0.0.5:8080, 10.0.0.6:8080, 10.0.0.7:8080]
```

### Three Types of Services

#### Type 1: ClusterIP (Default - Internal Only)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  type: ClusterIP
  selector:
    app: user-service
  ports:
  - port: 8080
    targetPort: 8080
```

**Access:**
```
From inside cluster only:
  http://user-service:8080
  http://user-service.default.svc.cluster.local:8080

From outside: NOT POSSIBLE
```

#### Type 2: NodePort (External Access)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  type: NodePort
  selector:
    app: user-service
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30008
```

**Access:**
```
From outside:
  http://192.168.1.2:30008/getUsers

From inside cluster:
  http://user-service:8080
```

#### Type 3: LoadBalancer (Cloud - External Access)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  type: LoadBalancer
  selector:
    app: user-service
  ports:
  - port: 8080
    targetPort: 8080
```

**Access:**
```
From outside:
  http://external-ip:8080
  (External IP assigned by cloud provider)

From inside cluster:
  http://user-service:8080
```

---

## Service vs Routing Table

### Where is kube-proxy?

```
kube-proxy runs on EVERY node:
├── Master node: kube-proxy running
├── Worker node 1: kube-proxy running
├── Worker node 2: kube-proxy running
└── Worker node 3: kube-proxy running

On your laptop (minikube):
├── Master: kube-proxy running
└── Worker: kube-proxy running (same machine)
```

### What Does kube-proxy Do?

kube-proxy has **TWO jobs:**

**Job 1: Watch API Server for Services**
```
kube-proxy continuously watches:
    "Give me all services"
    ↓
API Server returns:
    ├── Service "user-service"
    │   ├── IP: 10.96.0.1
    │   ├── Port: 8080
    │   ├── NodePort: 30008
    │   └── Endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]
    ├── Service "mysql-service"
    └── Service "auth-service"
```

**Job 2: Create iptables Rules Based on Service**
```
For each service, kube-proxy creates rules:

Service "user-service":
    ├── ClusterIP rule: 10.96.0.1:8080 → load balance to pods
    ├── NodePort rule: 192.168.1.2:30008 → load balance to pods
    └── Load balancing: Round-robin across endpoints
```

### Service is NOT the Load Balancer

```
Service = Configuration/Definition
    ├── Stored in etcd
    ├── Specifies: name, ports, selector, type
    └── Says: "Load balance to pods with label app=user-service"

kube-proxy = Implementation
    ├── Reads service definition
    ├── Creates iptables rules
    └── Actually performs load balancing

Load Balancing = The actual routing
    ├── Done by iptables (kernel-level)
    ├── Distributes traffic across pods
    └── Happens automatically
```

### Service is Like a Blueprint

```
Service = What (definition)
    ├── Stored in etcd
    ├── Specifies ports, selectors, type
    └── Shared across cluster

Routing Table = How (implementation)
    ├── Created on each node
    ├── Based on service definition
    └── Actual packet routing

Relationship:
Service → kube-proxy → Routing Table → Packet routing
```

### Detailed Comparison

#### Service (Blueprint)

```
Stored in: etcd (one place)
Content:
├── Name: user-service
├── Type: NodePort
├── Port: 8080
├── NodePort: 30008
├── Selector: app=user-service
└── Endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]

Accessed by: kube-proxy on each node
```

#### Routing Table (Built Structure)

```
Stored in: Kernel memory on each node
Content:
├── iptables rule 1: 192.168.1.20:30008 → 10.0.0.5:8080
├── iptables rule 2: 192.168.1.20:30008 → 10.0.0.6:8080
├── iptables rule 3: 192.168.1.20:30008 → 10.0.0.7:8080
├── iptables rule 4: 10.96.0.1:8080 → 10.0.0.5:8080
├── iptables rule 5: 10.96.0.1:8080 → 10.0.0.6:8080
└── iptables rule 6: 10.96.0.1:8080 → 10.0.0.7:8080

Created by: kube-proxy
Used by: Kernel for packet routing
```

### Multi-Node Example

```
etcd (Master):
    Service "user-service" (ONE definition)
    ├── Port: 8080
    ├── NodePort: 30008
    └── Endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]

Worker Node 1 (192.168.1.20):
    kube-proxy reads service
        ↓
    Creates routing table:
    ├── 192.168.1.20:30008 → 10.0.0.5:8080
    ├── 192.168.1.20:30008 → 10.0.0.6:8080
    └── 192.168.1.20:30008 → 10.0.0.7:8080

Worker Node 2 (192.168.1.21):
    kube-proxy reads service
        ↓
    Creates routing table:
    ├── 192.168.1.21:30008 → 10.0.0.5:8080
    ├── 192.168.1.21:30008 → 10.0.0.6:8080
    └── 192.168.1.21:30008 → 10.0.0.7:8080

Worker Node 3 (192.168.1.22):
    kube-proxy reads service
        ↓
    Creates routing table:
    ├── 192.168.1.22:30008 → 10.0.0.5:8080
    ├── 192.168.1.22:30008 → 10.0.0.6:8080
    └── 192.168.1.22:30008 → 10.0.0.7:8080

Result:
├── ONE service definition (etcd)
├── THREE routing tables (one per node)
└── All synchronized automatically
```

### Key Insight

```
Service is an ABSTRACTION LAYER:

High Level (Kubernetes):
  Service "user-service"
  └── Selector: app=user-service
  └── Endpoints: [10.0.0.5, 10.0.0.6, 10.0.0.7]

Low Level (Linux Kernel):
  iptables rules
  └── 30008 → 10.0.0.5:8080 (33%)
  └── 30008 → 10.0.0.6:8080 (33%)
  └── 30008 → 10.0.0.7:8080 (34%)

kube-proxy translates between them.
```

---

## Summary

**Service is NOT a routing table itself.**

**Service is a DEFINITION that kube-proxy uses to CREATE routing tables on each node.**

Or more simply:

**Service = SPECIFICATION**
**Routing Table = IMPLEMENTATION**

---

*Created: July 11, 2026*
*Kubernetes Networking & Architecture Guide*
