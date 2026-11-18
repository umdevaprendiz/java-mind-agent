import { chatHandlers } from './chat'
import { miscHandlers } from './misc'
import { modelHandlers } from './models'
import { pdfHandlers } from './pdf'

/** Todas as operações de `api/openapi.yaml`. O teste de contrato confere que nenhuma ficou de fora. */
export const handlers = [...chatHandlers, ...modelHandlers, ...pdfHandlers, ...miscHandlers]
