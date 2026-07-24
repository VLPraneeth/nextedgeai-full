import request from 'supertest';
import express from 'express';
import { initialize } from 'controllers/arcade';

jest.mock('utils/ConfigUtil', () => ({
  getConfigVariables: () => ({
    useMock: false,
    arcadeLogLevel: 'error',
    arcadeTarget: 'http://localhost:3001',
    secureCookies: false,
    trackResponseTime: false,
  }),
}));

describe('SSO Error Handling Test (with backend message)', () => {
  let app;
  let mockServer;

  beforeAll((done) => {
    mockServer = require('http').createServer((req, res) => {
      if (req.url.includes('not-found')) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'SSO endpoint not found' }));
      } else if (req.url.includes('success')) {
        res.writeHead(200, { 'Content-Type': 'application/json', authorization: 'mock-token' });
        res.end(JSON.stringify({ message: 'SSO login successful' }));
      } else {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Invalid SSO credentials' }));
      }
    });

    mockServer.listen(3001, () => {
      app = express();
      app.use(express.json());
      app.use(express.urlencoded({ extended: true }));
      initialize(app);
      done();
    });
  });

  afterAll((done) => {
    mockServer.close(done);
  });

  test('should handle SSO error 400 with backend message', async () => {
    const response = await request(app)
      .post('/sso/test-org/assertion')
      .send({ username: 'test@example.com', password: 'wrongpassword' })
      .expect(302);

    expect(response.headers.location).toMatch(/^\/errors\/error-400\?errorType=auth&message=/);
    expect(decodeURIComponent(response.headers.location)).toContain('Invalid SSO credentials');
  });

  test('should handle SSO error 404 with backend message', async () => {
    const response = await request(app)
      .post('/sso/not-found/assertion')
      .send({ username: 'test@example.com', password: 'wrongpassword' })
      .expect(302);

    expect(response.headers.location).toMatch(/^\/errors\/error-404\?errorType=notFound&message=/);
    expect(decodeURIComponent(response.headers.location)).toContain('SSO endpoint not found');
  });

  test('should handle SSO success and redirect to HOME_URL', async () => {
    const response = await request(app)
      .post('/sso/success/assertion')
      .send({ username: 'test@example.com', password: 'rightpassword' })
      .expect(302);

    expect(response.headers.location).toBe('/');
    expect(response.headers['set-cookie']).toBeDefined();
    expect(response.headers['set-cookie'][0]).toMatch(/authorization=/);
  });
});
