FROM node:22-bookworm-slim
WORKDIR /app
COPY agent/package*.json ./
RUN npm install --omit=dev && npm cache clean --force
COPY agent/server.js ./server.js
COPY agent/public ./public
COPY config ./config
RUN mkdir -p data documents applications logs && useradd --system --uid 10001 --create-home jobagent && chown -R jobagent:jobagent /app
USER jobagent
EXPOSE 8787
CMD ["node","server.js"]
