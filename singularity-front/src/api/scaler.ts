import axios from 'axios'
import type { ScalerPanelSnapshot, ServiceState } from './types'

export const scalerApi = {
  panel: async () => {
    const res = await axios.get<ScalerPanelSnapshot>('/api/scaler/panel')
    return res.data
  },
  status: async () => {
    const res = await axios.get<ServiceState[]>('/api/scaler/status')
    return res.data
  },
}
