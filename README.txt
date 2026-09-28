This README was made in order to ease the environment setup for the Fusion Operations Challenge. 
If you don't want to install Play Framework 1.7.1, Java 11 and MySQL 8.0 on your computer, we provide a multi container setup with all the tools you need to complete the challenge.

Before running the Fusion Operations Challenge App you will need to download and install:
- Docker (https://docs.docker.com/get-docker/)
- Docker compose (https://docs.docker.com/compose/install/)

The Docker environment is composed by two services:
- fops
- db

Dockerfile

The Dockerfile will build an image with a Ubuntu 22.04 image, install Python dependencies, install JDK 11, install Play Framework 1.7.1 and run the project.

docker-compose.yml

The "fops" service uses an image that’s built from the Dockerfile in the current directory. It then binds the container and the host machine to the exposed port, 9000.
This port is where you will access the running app: http://localhost:9000
You can notice an environment variable defined in the "fops" service: 'run'. This will launch your project in run mode. You can also launch it in 'test' or 'autotest' modes.

Database

The "db" service will launch a container with the MySQL 8.0 image. 
You have access to all the credentials there. The exposed port is the 1306, you can change it if you want to.
In the project folder conf/application.conf you have the app connection to the db that is running in the container.

You can use MySQL Workbench or any other client to connect to the MySQL server running in the container.

Launching the environment

To launch the environment enter the following command in your console: 
docker-compose up

Note that the first time the containers run all the content will be downloaded and may take some time.

About the environment

The environment is already setup with some boilerplate code, namely with the model definition as per the specification and the respective controllers.
It's making use of the Play CRUD module, you can find more information about this module on the documentation.

Feel free to modify the boilerplate code if you think it doesn't adapt to your solution.

Good luck!
