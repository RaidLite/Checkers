package yo.raidlite.checkers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var thread: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var board = IntArray(64)
    private var selectedIdx = -1
    private var isPlayerTurn = true
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    private enum class State { MENU, PLAYING, WHITE_WIN, RED_WIN }
    private var gameState = State.MENU

    init {
        holder.addCallback(this)
        resetGame()
    }

    private fun resetGame() {
        board = IntArray(64)
        for (i in 0 until 64) {
            val r = i / 8; val c = i % 8
            if ((r + c) % 2 != 0) {
                if (r < 3) board[i] = 2
                else if (r > 4) board[i] = 1
            }
        }
        isPlayerTurn = true
        selectedIdx = -1
    }

    override fun run() {
        while (running) {
            val canvas = holder.lockCanvas() ?: continue
            render(canvas)
            holder.unlockCanvasAndPost(canvas)

            if (gameState == State.PLAYING && !isPlayerTurn) {
                try { Thread.sleep(600) } catch (e: Exception) {}
                aiStep()
                checkGameOver()
                isPlayerTurn = true
            }
            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    private fun render(canvas: Canvas) {
        val minDim = width.coerceAtMost(height).toFloat()
        cellSize = minDim / 8
        offsetX = (width - minDim) / 2
        offsetY = (height - minDim) / 2

        canvas.drawColor(Color.parseColor("#222222"))

        if (gameState == State.MENU) {
            paint.color = Color.WHITE
            paint.textSize = cellSize * 0.8f
            canvas.drawText("START GAME", width / 2f - cellSize * 2, height / 2f, paint)
            return
        }

        for (i in 0 until 64) {
            val r = i / 8; val c = i % 8
            paint.color = if ((r + c) % 2 == 0) Color.parseColor("#E0C090") else Color.parseColor("#503010")
            val left = offsetX + c * cellSize
            val top = offsetY + r * cellSize
            canvas.drawRect(left, top, left + cellSize, top + cellSize, paint)

            val piece = board[i]
            if (piece != 0) {
                paint.color = when {
                    selectedIdx == i -> Color.YELLOW
                    piece == 1 || piece == 3 -> Color.WHITE
                    else -> Color.RED
                }
                canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.4f, paint)
                if (piece > 2) {
                    paint.color = Color.parseColor("#FFD700")
                    canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.15f, paint)
                }
            }
        }

        if (gameState != State.PLAYING) {
            paint.color = Color.argb(180, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textSize = cellSize * 0.7f
            val txt = if (gameState == State.WHITE_WIN) "WHITE WINS!" else "RED WINS!"
            canvas.drawText(txt, width / 2f - cellSize * 2, height / 2f, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        if (gameState != State.PLAYING) {
            resetGame()
            gameState = State.PLAYING
            return true
        }

        val c = ((event.x - offsetX) / cellSize).toInt()
        val r = ((event.y - offsetY) / cellSize).toInt()
        val idx = r * 8 + c

        if (idx in 0..63 && isPlayerTurn) {
            if (board[idx] == 1 || board[idx] == 3) {
                selectedIdx = idx
            } else if (selectedIdx != -1) {
                if (tryMove(selectedIdx, idx)) {
                    val captures = getCaptures(idx)
                    if (lastMoveWasCapture(selectedIdx, idx) && captures.isNotEmpty()) {
                        selectedIdx = idx
                    } else {
                        selectedIdx = -1
                        isPlayerTurn = false
                    }
                }
            }
        }
        return true
    }

    private fun lastMoveWasCapture(f: Int, t: Int) = abs(f / 8 - t / 8) >= 2

    private fun tryMove(f: Int, t: Int): Boolean {
        val possible = getValidMoves(f)
        val move = possible.find { it.to == t }
        if (move != null) {
            board[t] = board[f]
            board[f] = 0
            if (move.captured != -1) board[move.captured] = 0
            if (t / 8 == 0 && board[t] == 1) board[t] = 3
            if (t / 8 == 7 && board[t] == 2) board[t] = 4
            return true
        }
        return false
    }

    private class Move(val to: Int, val captured: Int = -1)

    private fun getValidMoves(f: Int): List<Move> {
        val captures = getCaptures(f)
        if (captures.isNotEmpty()) return captures

        val anyCapturePossible = (0..63).any { i ->
            val p = board[i]
            val myTeam = (board[f] == 1 || board[f] == 3)
            val pTeam = (p == 1 || p == 3)
            p != 0 && myTeam == pTeam && getCaptures(i).isNotEmpty()
        }
        if (anyCapturePossible) return emptyList()

        val moves = mutableListOf<Move>()
        val r = f / 8; val c = f % 8
        val dirs = if (board[f] <= 2) {
            if (board[f] == 1) listOf(-1 to -1, -1 to 1) else listOf(1 to -1, 1 to 1)
        } else listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

        for (d in dirs) {
            var nr = r + d.first; var nc = c + d.second
            while (nr in 0..7 && nc in 0..7) {
                val t = nr * 8 + nc
                if (board[t] == 0) {
                    moves.add(Move(t))
                    if (board[f] <= 2) break
                } else break
                nr += d.first; nc += d.second
            }
        }
        return moves
    }

    private fun getCaptures(f: Int): List<Move> {
        val captures = mutableListOf<Move>()
        val r = f / 8; val c = f % 8
        val isWhite = board[f] == 1 || board[f] == 3
        val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

        for (d in dirs) {
            var nr = r + d.first; var nc = c + d.second
            var enemyFound = -1
            while (nr in 0..7 && nc in 0..7) {
                val t = nr * 8 + nc
                if (board[t] != 0) {
                    val tWhite = board[t] == 1 || board[t] == 3
                    if (tWhite == isWhite || enemyFound != -1) break
                    enemyFound = t
                } else if (enemyFound != -1) {
                    captures.add(Move(t, enemyFound))
                    if (board[f] <= 2) break
                } else if (board[f] <= 2) break
                nr += d.first; nc += d.second
            }
        }
        return captures
    }

    private fun aiStep() {
        val allMoves = mutableListOf<Pair<Int, Move>>()
        for (i in 0 until 64) {
            if (board[i] == 2 || board[i] == 4) {
                getValidMoves(i).forEach { allMoves.add(i to it) }
            }
        }

        if (allMoves.isEmpty()) { gameState = State.WHITE_WIN; return }

        val captures = allMoves.filter { it.second.captured != -1 }
        val finalMove = if (captures.isNotEmpty()) captures[(captures.indices).random()]
        else allMoves[(0 until allMoves.size).random()]

        val (f, m) = finalMove
        board[m.to] = board[f]
        board[f] = 0
        if (m.captured != -1) {
            board[m.captured] = 0
            if (getCaptures(m.to).isNotEmpty()) {
                aiStep()
                return
            }
        }
        if (m.to / 8 == 7 && board[m.to] == 2) board[m.to] = 4
    }

    private fun checkGameOver() {
        val white = board.any { it == 1 || it == 3 }
        val red = board.any { it == 2 || it == 4 }
        if (!white) gameState = State.RED_WIN
        if (!red) gameState = State.WHITE_WIN
    }

    override fun surfaceCreated(h: SurfaceHolder) { running = true; thread = Thread(this).apply { start() } }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h1: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        var retry = true
        while (retry) {
            try {
                thread?.join()
                retry = false
            } catch (e: Exception) {}
        }
    }
}