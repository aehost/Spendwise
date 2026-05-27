FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --ignore-scripts
COPY . .
RUN npm run build
EXPOSE 3002
CMD ["npm", "start"]
