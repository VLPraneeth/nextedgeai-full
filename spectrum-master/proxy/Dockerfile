FROM node:18

WORKDIR /app
COPY . .
RUN npm install
RUN npm run build

EXPOSE 8088

CMD ["node", "dist/index.js"]