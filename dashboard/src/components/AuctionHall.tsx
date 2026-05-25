import { motion, AnimatePresence } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { Gavel } from 'lucide-react'

export function AuctionHall() {
  const auctions = useHiveStore(s => s.auctions)

  const recent = auctions.slice(-8)

  return (
    <div className="flex flex-col gap-3 h-full overflow-y-auto pr-1">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2">
        <Gavel className="w-3.5 h-3.5" /> Auction Hall
      </h2>

      {recent.length === 0 && (
        <p className="text-xs text-slate-600 italic">Nenhum leilão...</p>
      )}

      <AnimatePresence>
        {recent.map(auction => (
          <motion.div
            key={auction.task}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="flex items-center gap-3 rounded-lg border border-border-dim bg-surface-card/50 px-3 py-2"
          >
            <span className="text-sm font-mono font-semibold text-slate-300 w-20 truncate">
              {auction.task}
            </span>

            <div className="flex gap-2 flex-1 flex-wrap">
              {auction.bids.map(bid => (
                <div
                  key={bid.squad}
                  className={`flex items-center gap-1.5 text-[11px] font-mono px-2 py-0.5 rounded ${
                    bid.winner
                      ? 'bg-neon-green/15 text-neon-green border border-neon-green/30'
                      : 'bg-slate-800/50 text-slate-500'
                  }`}
                >
                  <span>{bid.squad}</span>
                  <span className="font-semibold">{bid.value.toFixed(1)}</span>
                  {bid.winner && <span className="text-[9px] uppercase">WIN</span>}
                </div>
              ))}
            </div>

            <span className={`text-[10px] font-semibold uppercase tracking-wider ${
              auction.resolved ? 'text-neon-green' : 'text-neon-amber'
            }`}>
              {auction.resolved ? 'RESOLVED' : 'BIDDING'}
            </span>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
