FROM node:22-alpine AS build
WORKDIR /app

ARG BUILD_CONFIGURATION=prod

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
RUN npm run build -- --configuration=${BUILD_CONFIGURATION}

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/todo-ui/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
