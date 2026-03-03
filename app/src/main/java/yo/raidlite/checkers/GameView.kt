package yo.raidlite.checkers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.sin

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var thread: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var board = IntArray(64)
    private var selectedIdx = -1
    private var isPlayerTurn = true
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var frameTime = 0f

    private enum class State { MENU, PLAYING, WHITE_WIN, RED_WIN }
    private var gameState = State.MENU

    init {
        holder.addCallback(this)
        resetGame()
    }

    private fun resetGame() {
        board = IntArray(64)
        for (i in 0 until 64) {
            val r = i / 8
            val c = i % 8
            if ((r + c) % 2 != 0) {
                when {
                    r < 3 -> board[i] = 2
                    r > 4 -> board[i] = 1
                }
            }
        }
        isPlayerTurn = true
        selectedIdx = -1
    }

    override fun run() {
        while (running) {
            val canvas = holder.lockCanvas() ?: continue
            frameTime += 0.1f
            render(canvas)
            holder.unlockCanvasAndPost(canvas)

            if (gameState == State.PLAYING && !isPlayerTurn) {
                Thread.sleep(400)
                aiStep()
                checkGameOver()
                isPlayerTurn = true
            }
            Thread.sleep(16)
        }
    }

    private fun render(canvas: Canvas) {
        val minDim = width.coerceAtMost(height).toFloat()
        cellSize = minDim / 8
        offsetX = (width - minDim) / 2
        offsetY = (height - minDim) / 2

        canvas.drawColor(Color.parseColor("#1A1A1A"))

        if (gameState == State.MENU) {
            drawMenu(canvas)
            return
        }

        for (i in 0 until 64) {
            val left = offsetX + (i % 8) * cellSize
            val top = offsetY + (i / 8) * cellSize

            paint.color = if (((i / 8) + (i % 8)) % 2 == 0) Color.parseColor("#332A22") else Color.parseColor("#1E1E1E")
            canvas.drawRect(left, top, left + cellSize, top + cellSize, paint)

            val piece = board[i]
            if (piece != 0) {
                val pulse = if (selectedIdx == i) abs(sin(frameTime.toDouble())).toFloat() * 15 else 0f

                paint.color = when {
                    selectedIdx == i -> Color.parseColor("#FFD700")
                    piece == 1 || piece == 3 -> Color.WHITE
                    else -> Color.parseColor("#CF352E")
                }

                canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, (cellSize * 0.38f) + pulse / 5, paint)

                if (piece > 2) {
                    paint.color = Color.parseColor("#FFD700")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f
                    canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize * 0.2f, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }

        if (gameState != State.PLAYING) drawGameOver(canvas)
    }

    private fun drawMenu(canvas: Canvas) {
        val scale = 1f + sin(frameTime.toDouble()).toFloat() * 0.05f
        paint.color = Color.WHITE
        paint.textSize = cellSize * 0.8f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.save()
        canvas.scale(scale, scale, width / 2f, height / 2f)
        canvas.drawText("TAP TO START", width / 2f, height / 2f, paint)
        canvas.restore()
    }

    private fun drawGameOver(canvas: Canvas) {
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = cellSize * 0.7f
        canvas.drawText(if (gameState == State.WHITE_WIN) "VICTORY" else "DEFEAT", width / 2f, height / 2f, paint)
        paint.textSize = cellSize * 0.3f
        canvas.drawText("Tap to restart", width / 2f, height / 2f + cellSize, paint)
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
                    if (abs(selectedIdx / 8 - idx / 8) >= 2 && getCaptures(idx).isNotEmpty()) {
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

    private fun tryMove(f: Int, t: Int): Boolean {
        val move = getValidMoves(f).find { it.to == t } ?: return false
        board[t] = board[f]
        board[f] = 0
        if (move.captured != -1) board[move.captured] = 0
        if (t / 8 == 0 && board[t] == 1) board[t] = 3
        if (t / 8 == 7 && board[t] == 2) board[t] = 4
        return true
    }

    private class Move(val to: Int, val captured: Int = -1, var weight: Int = 0)

    private fun getValidMoves(f: Int): List<Move> {
        val captures = getCaptures(f)
        if (captures.isNotEmpty()) return captures

        val anyCap = (0..63).any { (board[it] == board[f] || board[it] == board[f] + 2) && getCaptures(it).isNotEmpty() }
        if (anyCap) return emptyList()

        val moves = mutableListOf<Move>()
        val dirs = if (board[f] <= 2) (if (board[f] == 1) listOf(-1 to -1, -1 to 1) else listOf(1 to -1, 1 to 1))
        else listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

        for (d in dirs) {
            var nr = (f / 8) + d.first
            var nc = (f % 8) + d.second
            while (nr in 0..7 && nc in 0..7) {
                val t = nr * 8 + nc
                if (board[t] == 0) {
                    moves.add(Move(t))
                    if (board[f] <= 2) break
                } else break
                nr += d.first
                nc += d.second
            }
        }
        return moves
    }

    private fun getCaptures(f: Int): List<Move> {
        val captures = mutableListOf<Move>()
        val isWhite = board[f] == 1 || board[f] == 3
        val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (d in dirs) {
            var nr = (f / 8) + d.first
            var nc = (f % 8) + d.second
            var enemy = -1
            while (nr in 0..7 && nc in 0..7) {
                val t = nr * 8 + nc
                if (board[t] != 0) {
                    if ((board[t] == 1 || board[t] == 3) == isWhite || enemy != -1) break
                    enemy = t
                } else if (enemy != -1) {
                    captures.add(Move(t, enemy))
                    if (board[f] <= 2) break
                } else if (board[f] <= 2) break
                nr += d.first
                nc += d.second
            }
        }
        return captures
    }

    private fun aiStep() {
        val moves = mutableListOf<Pair<Int, Move>>()
        for (i in 0 until 64) if (board[i] == 2 || board[i] == 4) getValidMoves(i).forEach { moves.add(i to it) }

        if (moves.isEmpty()) { gameState = State.WHITE_WIN; return }

        moves.forEach { (f, m) ->
            m.weight = when {
                m.captured != -1 -> 100
                m.to / 8 == 7 && board[f] == 2 -> 50
                m.to % 8 == 0 || m.to % 8 == 7 -> 10
                else -> 0
            }
        }

        val best = moves.filter {
            it.second.weight == moves.maxByOrNull {
                it.second.weight
            }?.second?.weight
        }.random()
        val (f, m) = best
        board[m.to] = board[f]
        board[f] = 0
        if (m.captured != -1) {
            board[m.captured] = 0
            if (getCaptures(m.to).isNotEmpty()) { aiStep(); return }
        }
        if (m.to / 8 == 7 && board[m.to] == 2) board[m.to] = 4
    }

    private fun checkGameOver() {
        val white = board.any { it == 1 || it == 3 }
        val red = board.any { it == 2 || it == 4 }
        if (!white) gameState = State.RED_WIN else if (!red) gameState = State.WHITE_WIN
    }

    override fun surfaceCreated(h: SurfaceHolder) { running = true; thread = Thread(this).apply { start() } }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h1: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (_: Exception) {}
    }
}