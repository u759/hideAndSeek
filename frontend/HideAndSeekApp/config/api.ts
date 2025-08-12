// Configuration for different environments
const getApiBaseUrl = () => {
  // If running in development with Expo
  if (__DEV__) {
    // Use your local network IP for Spring Boot backend
    return 'http://192.168.1.147:8080/api';
  }
  
  // Production URL would go here
  return 'http://your-production-api.com/api';
};

export const API_BASE_URL = getApiBaseUrl();
