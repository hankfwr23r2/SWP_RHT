import { useCallback, useEffect, useState } from 'react'

// Gọi một hàm service bất đồng bộ và giữ kết quả. reload() để tải lại sau khi cập nhật.
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>(undefined)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  const reload = useCallback(() => { run().then(setData) }, [run])
  useEffect(() => { reload() }, [reload])
  return { data, reload }
}
