db = db.getSiblingDB('local_db');

db.createCollection('policy');

db.policy.insertMany([
    // Original Policy
    {
        _id: ObjectId("685ad5e2c27b1625e6253c36"),
        policyStatus: "ACTIVE",
        productReference: {
            version: "0.3.0",
            name: "Allianz BMP Motor Product",
            groupNameApl: "9e4f116b-05aa-4535-95e6-f02ca3446635",
            groupName: "MOTOR",
            code: "RMTP"
        },
        insuredProperty: {
            _id: "d22ca1cd-f74e-4800-b419-51c568960308",
            type: "S47",
            address: {
                fullAddress: "Linzer Str.  , WIEN 1010",
                streetDetails: {
                    name: "Linzer Str.",
                    nameType: " ",
                    number: "225",
                    numberType: "STNO"
                },
                country: "AUT",
                state: "VI",
                countryCode: "AUT",
                city: "WIEN",
                postalCode: "1010",
                _class: "com.allianz.bmp.claims.common.data.aggregator.application.ausaal.policy.adapter.persistence.entity.AddressAusAalEntity"
            }
        },
        beginDate: new Date(1731628800000),
        endDate: new Date(1763164800000),
        policyNumber: "MTRHHR00026397",
        policyHolders: [
            {
                _id: "0ae2f5c8-f2bc-41cd-8265-44b4119795b8",
                firstName: "Lukas",
                lastName: "Baumgartner",
                dateOfBirth: new Date(753494400000),
                gender: "M",
                address: {
                    fullAddress: "7 REST CT, SPRINGFIELD LAKES QLD 4300",
                    streetDetails: {
                        name: "REST",
                        nameType: "CT",
                        number: "7",
                        numberType: "STNO"
                    },
                    country: "AUS",
                    state: "QL",
                    countryCode: "AUS",
                    city: "SPRINGFIELD LAKES",
                    postalCode: "4300",
                    _class: "com.allianz.bmp.claims.common.data.aggregator.application.ausaal.policy.adapter.persistence.entity.AddressAusAalEntity"
                },
                contactChannels: [
                    {
                        communicationDetails: "allianz@test.at",
                        communicationType: "EMAIL"
                    },
                    {
                        communicationDetails: "+61456677674",
                        communicationType: "MOBILE"
                    }
                ],
                customerId: "V20F49795762C4526018FDEF311E2365E5BFD11D6DEC965B554DE52CB4DA905C65A046258DC566B7B77B4385F000BF6AF4",
                isPolicyHolderPayer: true,
                roles: ["LANDLORD"],
                _class: "com.allianz.bmp.claims.common.data.aggregator.core.policy.adapter.persistence.entity.common.policyholder.NaturalPersonPolicyHolderEntity"
            }
        ],
        _class: "com.allianz.bmp.claims.common.data.aggregator.core.policy.adapter.persistence.entity.retail.home.RetailHomePolicyEntity"
    },
    {
        _id: ObjectId(),
        policyStatus: "EXPIRED",
        productReference: {
            version: "1.0.1",
            name: "Allianz Home Basic",
            groupNameApl: "a1b2c3d4-5678-9101-1121-abcdefabcdef",
            groupName: "HOME",
            code: "HMBP"
        },
        insuredProperty: {
            _id: "33dc2341-df02-44f4-a2d9-aaa120b9c111",
            type: "H24",
            address: {
                fullAddress: "10 King St, Melbourne VIC 3000",
                streetDetails: {
                    name: "King St",
                    nameType: "ST",
                    number: "10",
                    numberType: "STNO"
                },
                country: "AUS",
                state: "VIC",
                countryCode: "AUS",
                city: "Melbourne",
                postalCode: "3000",
                _class: "com.allianz.bmp.claims.common.data.aggregator.application.ausaal.policy.adapter.persistence.entity.AddressAusAalEntity"
            }
        },
        beginDate: new Date(1609459200000),
        endDate: new Date(1640995200000),
        policyNumber: "HMB1234567890",
        policyHolders: [
            {
                _id: "12345678-aaaa-bbbb-cccc-112233445566",
                firstName: "Emily",
                lastName: "Smith",
                dateOfBirth: new Date(631152000000),
                gender: "F",
                address: {
                    fullAddress: "10 King St, Melbourne VIC 3000",
                    streetDetails: {
                        name: "King St",
                        nameType: "ST",
                        number: "10",
                        numberType: "STNO"
                    },
                    country: "AUS",
                    state: "VIC",
                    countryCode: "AUS",
                    city: "Melbourne",
                    postalCode: "3000",
                    _class: "com.allianz.bmp.claims.common.data.aggregator.application.ausaal.policy.adapter.persistence.entity.AddressAusAalEntity"
                },
                contactChannels: [
                    {
                        communicationDetails: "emily.smith@email.com",
                        communicationType: "EMAIL"
                    }
                ],
                customerId: "ABC123XYZ456",
                isPolicyHolderPayer: true,
                roles: ["OWNER"],
                _class: "com.allianz.bmp.claims.common.data.aggregator.core.policy.adapter.persistence.entity.common.policyholder.NaturalPersonPolicyHolderEntity"
            }
        ],
        _class: "com.allianz.bmp.claims.common.data.aggregator.core.policy.adapter.persistence.entity.retail.home.RetailHomePolicyEntity"
    }
]);

db.createCollection('whathappened');

db.whathappened.insertMany([
    {
        "whatHappenCode": "EDM",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Fenomeno Elettrico"
    },
    {
        "whatHappenCode": "GB",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Danni accidentali ai Vetri"
    },
    {
        "whatHappenCode": "MLO",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Eccedenza consumo d'acqua"
    },
    {
        "whatHappenCode": "TLB",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Rischio Locativo"
    },
    {
        "whatHappenCode": "FIR",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Incendio e fumo"
    },
    {
        "whatHappenCode": "LTN",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Azione diretta del fulmine"
    },
    {
        "whatHappenCode": "EXP",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Esplosione, Implosione"
    },
    {
        "whatHappenCode": "SOW",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Onda Sonica"
    },
    {
        "whatHappenCode": "FOJ",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "ani"
    },
    {
        "whatHappenCode": "IGV",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Urto di Veicolo Stradale"
    },
    {
        "whatHappenCode": "IWV",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Urto di Natante"
    },
    {
        "whatHappenCode": "IAV",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Urto di Aereoveicolo"
    },
    {
        "whatHappenCode": "ESW",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Fuoriuscita di Acqua e Altri Liquidi"
    },
    {
        "whatHappenCode": "ESG",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Fuoriuscita di gas"
    },
    {
        "whatHappenCode": "NTV",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Eventi Atmosferici"
    },
    {
        "whatHappenCode": "NTV",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Allagamento"
    },
    {
        "whatHappenCode": "RIO",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Tumulto popolare / Sommosse"
    },
    {
        "whatHappenCode": "VMA",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Atti Vandalici / Dolosi"
    },
    {
        "whatHappenCode": "TER",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Terrorismo"
    },
    {
        "whatHappenCode": "BUR",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Furto/Tentato furto in casa - Guasti Ladri e Furto Porte/Finestre"
    },
    {
        "whatHappenCode": "ROB",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Rapina in casa"
    },
    {
        "whatHappenCode": "FRA",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Truffa in casa"
    },
    {
        "whatHappenCode": "THE",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Furto all'esterno dell'abitazione"
    },
    {
        "whatHappenCode": "ROE",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Rapina all'esterno dell'abitazione"
    },
    {
        "whatHappenCode": "EART",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Terremoto"
    },
    {
        "whatHappenCode": "FLOO",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Alluvione e Inondazione"
    },
    {
        "whatHappenCode": "ACC",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Infortunio"
    },
    {
        "whatHappenCode": "ILL",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Malattia"
    },
    {
        "whatHappenCode": "PREG",
        "claimClassGroup": "OWNDAMAGE",
        "whatHappenedContext": "Parto Cesareo"
    },
    {
        "whatHappenCode": "ACMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "RC- Vita Privata"
    },
    {
        "whatHappenCode": "BEL",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "RC- Infortunio collaboratore domestico/familiare"
    },
    {
        "whatHappenCode": "IUL",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "RC - Minori sui social network"
    },
    {
        "whatHappenCode": "LLMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "RC - Affittacamere e BeB"
    },
    {
        "whatHappenCode": "PLOFM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "PLUFM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "PLOWM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "PLUWM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "PLOAM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "PLUAM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "DOMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "DHMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Custodia"
    },
    {
        "whatHappenCode": "HOMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "HHMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Custodia"
    },
    {
        "whatHappenCode": "AOMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "AHMD",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Custodia"
    },
    {
        "whatHappenCode": "ELOFM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "ELUFM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "ELOWM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "ELUWM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "ELOAM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Proprietà"
    },
    {
        "whatHappenCode": "ELUAM",
        "claimClassGroup": "LIABILITY",
        "whatHappenedContext": "Conduzione"
    },
    {
        "whatHappenCode": "LPOB",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Controversie relative alla Casa"
    },
    {
        "whatHappenCode": "LPPL",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Controversie relative alla Famiglia"
    },
    {
        "whatHappenCode": "LPEM",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Estensione Controversie con il Datore di Lavoro"
    },
    {
        "whatHappenCode": "LPEB",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Estensione Controversie abitazioni di proprietà non Locate"
    },
    {
        "whatHappenCode": "LPTA",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Controversie relative ai veicoli guidati con patente - incidente"
    },
    {
        "whatHappenCode": "LPNTA",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Controversie relative ai veicoli guidati con patente - senza incidente"
    },
    {
        "whatHappenCode": "LPGC",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Risarcimento garantito"
    },
    {
        "whatHappenCode": "LPCR",
        "claimClassGroup": "LEGAL",
        "whatHappenedContext": "Rimborsi Complementari per veicoli guidati con patente"
    }
])
