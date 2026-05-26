const http = require('http');
const { Server } = require('socket.io');

const server = http.createServer((req, res) => {
  // Health check لـ Render
  if (req.url === '/health') {
    res.writeHead(200);
    res.end('OK');
  }
});

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  },
  pingTimeout: 60000,
  pingInterval: 25000
});

const rooms = new Map();

io.on('connection', (socket) => {
  console.log('جهاز متصل:', socket.id);

  // انضمام لغرفة
  socket.on('join', (roomId) => {
    socket.join(roomId);
    
    if (!rooms.has(roomId)) {
      rooms.set(roomId, []);
    }
    rooms.get(roomId).push(socket.id);
    
    const clients = rooms.get(roomId);
    
    // إذا في غرفة شخصان — ابدأ الاتصال
    if (clients.length === 2) {
      io.to(roomId).emit('ready');
    }
    
    console.log(`غرفة ${roomId} — عدد المتصلين: ${clients.length}`);
  });

  // إرسال Offer
  socket.on('offer', ({ roomId, sdp }) => {
    socket.to(roomId).emit('offer', sdp);
    console.log('offer أُرسل للغرفة:', roomId);
  });

  // إرسال Answer
  socket.on('answer', ({ roomId, sdp }) => {
    socket.to(roomId).emit('answer', sdp);
    console.log('answer أُرسل للغرفة:', roomId);
  });

  // إرسال ICE Candidate
  socket.on('ice-candidate', ({ roomId, candidate }) => {
    socket.to(roomId).emit('ice-candidate', candidate);
  });

  // قطع الاتصال
  socket.on('disconnect', () => {
    rooms.forEach((clients, roomId) => {
      const index = clients.indexOf(socket.id);
      if (index !== -1) {
        clients.splice(index, 1);
        socket.to(roomId).emit('peer-disconnected');
        console.log(`جهاز غادر الغرفة: ${roomId}`);
      }
    });
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`✅ السيرفر يعمل على البورت ${PORT}`);
});