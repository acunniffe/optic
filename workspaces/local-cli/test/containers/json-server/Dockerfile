FROM node
WORKDIR /usr/src/app
RUN npm install -g json-server
RUN YARN_REGISTRY=http://localhost:4873 yarn global add @useoptic/cli --registry=http://localhost:4873

COPY package.json ./
COPY yarn.lock ./
RUN yarn install

COPY *db.json ./
COPY optic.yml ./
COPY index.js ./

CMD api start
EXPOSE 4005
