FROM node:18

WORKDIR /app
COPY . .

RUN npm install

RUN npx ts-node scripts/lessToTs.ts

EXPOSE 3000

CMD ["node", "scripts/start.js"]