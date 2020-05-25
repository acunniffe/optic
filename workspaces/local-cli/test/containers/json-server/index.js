const jsonServer = require('json-server');
const app = jsonServer.create();

app.get('/optic-state', (req, res) => {
  res.json({ valid: true });
});

const router = jsonServer.router(process.env.DB_NAME || 'db.json');
const middlewares = jsonServer.defaults();

app.use(middlewares);
app.use(router);
app.listen(process.env.OPTIC_API_PORT || 5000, () => {
  console.log('JSON Server is running');
  console.log('Get data for you assertions via GET /optic-state ');
});
