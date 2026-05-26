const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

app.get('/', (req, res) => {
  res.send('WebRTC Signaling Server is Running!');
});

io.on('connection', (socket) => {
  console.log('User connected:', socket.id);

  // الانضمام لغرفة خاصة بالجهاز للربط بين الأب والابن
  socket.on('join-room', (roomId) => {
    socket.join(roomId);
    console.log(`Socket ${socket.id} joined room: ${roomId}`);
  });

  // إرسال العرض (Offer)
  socket.on('offer', (data) => {
    socket.to(data.roomId).emit('offer', {
      sdp: data.sdp,
      sender: socket.id
    });
  });

  // إرسال الإجابة (Answer)
  socket.on('answer', (data) => {
    socket.to(data.roomId).emit('answer', {
      sdp: data.sdp,
      sender: socket.id
    });
  });

  // تبادل مرشحات الاتصال المباشر (ICE Candidates)
  socket.on('ice-candidate', (data) => {
    socket.to(data.roomId).emit('ice-candidate', {
      candidate: data.candidate,
      sender: socket.id
    });
  });

  socket.on('disconnect', () => {
    console.log('User disconnected:', socket.id);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});