export const topicRoutes = (topicRoutes: {topic: string; targetPath: string}[]) =>
    topicRoutes.map(({topic, targetPath}) => `${topic}:${targetPath}`).join(',');
